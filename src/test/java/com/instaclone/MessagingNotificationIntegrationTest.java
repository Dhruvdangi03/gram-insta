package com.instaclone;

import static org.assertj.core.api.Assertions.assertThat;

import com.instaclone.auth.filter.AuthRateLimitFilter;
import com.instaclone.notification.entity.Notification;
import com.instaclone.notification.enums.NotificationType;
import com.instaclone.notification.event.NotificationConsumer;
import com.instaclone.notification.repository.NotificationRepository;
import com.instaclone.user.entity.User;
import com.instaclone.user.repository.UserRepository;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * Phase 3's "you'll know it works when": two accounts can message in real time, and one gets a
 * notification when the other likes their post. Both the durable (REST/DB) and live (STOMP push)
 * halves of each pipeline are verified independently, matching how Phase 2's async transcode
 * pipeline was verified both ways.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = AuthRateLimitFilter.ENABLED_PROPERTY + "=false")
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class MessagingNotificationIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private NotificationConsumer notificationConsumer;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${local.server.port}")
    private int port;

    @Test
    void messagingWorksLiveAndOverRestFallback() throws Exception {
        String aliceToken = register("msg_alice", "msg_alice@example.com");
        String bobToken = register("msg_bob", "msg_bob@example.com");
        // Bob follows Alice so her conversation lands in his inbox rather than his message requests.
        rest.exchange("/users/msg_alice/follow", HttpMethod.POST, new HttpEntity<>(null, bearer(bobToken)), Map.class);

        ResponseEntity<Map> convoResponse = rest.exchange(
                "/conversations",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("participantUsernames", List.of("msg_bob")), bearer(aliceToken)),
                Map.class);
        assertThat(convoResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number conversationId = (Number) convoResponse.getBody().get("id");

        StompSession bobSession = connect(bobToken);
        BlockingQueue<Map> bobMessages = new ArrayBlockingQueue<>(10);
        bobSession.subscribe("/user/queue/messages", frameHandler(Map.class, bobMessages));

        StompSession aliceSession = connect(aliceToken);
        aliceSession.send(
                "/app/conversations/" + conversationId + "/messages", Map.of("content", "hello over websocket"));

        Map liveMessage = bobMessages.poll(10, TimeUnit.SECONDS);
        assertThat(liveMessage).as("bob should receive the message live over STOMP").isNotNull();
        assertThat(liveMessage.get("content")).isEqualTo("hello over websocket");
        assertThat(((Map) liveMessage.get("sender")).get("username")).isEqualTo("msg_alice");

        // Independently confirm it's durable, not just delivered live.
        ResponseEntity<Map> historyAfterLive = rest.exchange(
                "/conversations/" + conversationId + "/messages", HttpMethod.GET, new HttpEntity<>(bearer(bobToken)), Map.class);
        List<Map<String, Object>> itemsAfterLive = (List<Map<String, Object>>) historyAfterLive.getBody().get("items");
        assertThat(itemsAfterLive).anyMatch(m -> "hello over websocket".equals(m.get("content")));

        // REST-only send while bob's socket isn't subscribed to anything new — the doc's explicit
        // "REST history endpoint as the fallback for anything sent while offline."
        ResponseEntity<Map> restSend = rest.exchange(
                "/conversations/" + conversationId + "/messages",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("content", "hello over rest"), bearer(aliceToken)),
                Map.class);
        assertThat(restSend.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> historyAfterRest = rest.exchange(
                "/conversations/" + conversationId + "/messages", HttpMethod.GET, new HttpEntity<>(bearer(bobToken)), Map.class);
        List<Map<String, Object>> itemsAfterRest = (List<Map<String, Object>>) historyAfterRest.getBody().get("items");
        assertThat(itemsAfterRest).anyMatch(m -> "hello over rest".equals(m.get("content")));

        aliceSession.disconnect();
        bobSession.disconnect();
    }

    @Test
    void likingAPostNotifiesTheAuthorLiveAndDurably() throws Exception {
        String aliceToken = register("notif_alice", "notif_alice@example.com");
        String bobToken = register("notif_bob", "notif_bob@example.com");

        Number bobPostId = createPhotoPost(bobToken, "bob's post");

        StompSession bobSession = connect(bobToken);
        BlockingQueue<Map> bobNotifications = new ArrayBlockingQueue<>(10);
        bobSession.subscribe("/user/queue/notifications", frameHandler(Map.class, bobNotifications));

        rest.exchange(
                "/posts/" + bobPostId + "/likes", HttpMethod.POST, new HttpEntity<>(null, bearer(aliceToken)), Map.class);

        Map liveNotification = bobNotifications.poll(10, TimeUnit.SECONDS);
        assertThat(liveNotification).as("bob should receive a live notification when alice likes his post").isNotNull();
        assertThat(liveNotification.get("type")).isEqualTo("LIKE");
        assertThat(((Map) liveNotification.get("actor")).get("username")).isEqualTo("notif_alice");

        ResponseEntity<Map> notificationsResponse =
                rest.exchange("/notifications", HttpMethod.GET, new HttpEntity<>(bearer(bobToken)), Map.class);
        List<Map<String, Object>> items = (List<Map<String, Object>>) notificationsResponse.getBody().get("items");
        assertThat(items).anyMatch(n -> "LIKE".equals(n.get("type")));

        bobSession.disconnect();
    }

    // Regression test: FOLLOW_REQUEST / FOLLOW_REQUEST_ACCEPTED were added to the Java enum without
    // widening the notifications table's CHECK constraint, so the consumer's insert failed (caught,
    // logged, and ACKed) and a private account's owner never saw a notification at all — while
    // every other test stayed green, since none of them exercised this path.
    @Test
    void followRequestAndItsAcceptanceNotifyBothPartiesLiveAndDurably() throws Exception {
        String ownerToken = register("req_owner", "req_owner@example.com");
        String requesterToken = register("req_requester", "req_requester@example.com");
        rest.exchange(
                "/users/me", HttpMethod.PATCH, new HttpEntity<>(Map.of("isPrivate", true), bearer(ownerToken)), Map.class);

        StompSession ownerSession = connect(ownerToken);
        BlockingQueue<Map> ownerNotifications = new ArrayBlockingQueue<>(10);
        ownerSession.subscribe("/user/queue/notifications", frameHandler(Map.class, ownerNotifications));
        StompSession requesterSession = connect(requesterToken);
        BlockingQueue<Map> requesterNotifications = new ArrayBlockingQueue<>(10);
        requesterSession.subscribe("/user/queue/notifications", frameHandler(Map.class, requesterNotifications));

        ResponseEntity<Map> followResponse = rest.exchange(
                "/users/req_owner/follow", HttpMethod.POST, new HttpEntity<>(null, bearer(requesterToken)), Map.class);
        assertThat(followResponse.getBody().get("status")).isEqualTo("PENDING");

        Map requestNotification = ownerNotifications.poll(10, TimeUnit.SECONDS);
        assertThat(requestNotification)
                .as("the private account's owner should be notified live of an incoming follow request")
                .isNotNull();
        assertThat(requestNotification.get("type")).isEqualTo("FOLLOW_REQUEST");
        assertThat(((Map) requestNotification.get("actor")).get("username")).isEqualTo("req_requester");
        assertThat(notificationTypes(ownerToken)).contains("FOLLOW_REQUEST");

        rest.exchange(
                "/users/req_requester/follow/accept", HttpMethod.POST, new HttpEntity<>(null, bearer(ownerToken)), Map.class);

        Map acceptedNotification = requesterNotifications.poll(10, TimeUnit.SECONDS);
        assertThat(acceptedNotification).as("the requester should hear that their request was accepted").isNotNull();
        assertThat(acceptedNotification.get("type")).isEqualTo("FOLLOW_REQUEST_ACCEPTED");
        assertThat(((Map) acceptedNotification.get("actor")).get("username")).isEqualTo("req_owner");
        assertThat(notificationTypes(requesterToken)).contains("FOLLOW_REQUEST_ACCEPTED");

        ownerSession.disconnect();
        requesterSession.disconnect();
    }

    @Test
    void cancellingOrDecliningAPendingRequestClearsTheOwnersNotification() throws Exception {
        String ownerToken = register("clr_owner", "clr_owner@example.com");
        String cancellerToken = register("clr_canceller", "clr_canceller@example.com");
        String declinedToken = register("clr_declined", "clr_declined@example.com");
        rest.exchange(
                "/users/me", HttpMethod.PATCH, new HttpEntity<>(Map.of("isPrivate", true), bearer(ownerToken)), Map.class);

        StompSession ownerSession = connect(ownerToken);
        BlockingQueue<Map> ownerNotifications = new ArrayBlockingQueue<>(10);
        ownerSession.subscribe("/user/queue/notifications", frameHandler(Map.class, ownerNotifications));
        BlockingQueue<Map> ownerChanges = new ArrayBlockingQueue<>(10);
        ownerSession.subscribe("/user/queue/notifications-changed", frameHandler(Map.class, ownerChanges));

        rest.exchange(
                "/users/clr_owner/follow", HttpMethod.POST, new HttpEntity<>(null, bearer(cancellerToken)), Map.class);
        rest.exchange(
                "/users/clr_owner/follow", HttpMethod.POST, new HttpEntity<>(null, bearer(declinedToken)), Map.class);
        // The notification row is written asynchronously via the Redis stream — wait until both have
        // actually landed (the live push only fires after the row is saved) before clearing them.
        assertThat(ownerNotifications.poll(10, TimeUnit.SECONDS)).isNotNull();
        assertThat(ownerNotifications.poll(10, TimeUnit.SECONDS)).isNotNull();
        assertThat(notificationTypes(ownerToken)).containsExactly("FOLLOW_REQUEST", "FOLLOW_REQUEST");

        rest.exchange(
                "/users/clr_owner/follow", HttpMethod.DELETE, new HttpEntity<>(null, bearer(cancellerToken)), Void.class);
        assertThat(notificationTypes(ownerToken)).containsExactly("FOLLOW_REQUEST");
        assertThat(ownerChanges.poll(10, TimeUnit.SECONDS))
                .as("the owner's open session is told a notification was removed, so its list/count update live")
                .isNotNull();

        rest.exchange(
                "/users/clr_declined/follow/reject", HttpMethod.DELETE, new HttpEntity<>(null, bearer(ownerToken)), Void.class);
        assertThat(notificationTypes(ownerToken)).isEmpty();
        assertThat(ownerChanges.poll(10, TimeUnit.SECONDS)).isNotNull();

        ownerSession.disconnect();
    }

    @Test
    void acceptingDecliningOrGoingPublicAlsoClearsTheOwnersRequestNotification() throws Exception {
        String ownerToken = register("res_owner", "res_owner@example.com");
        String acceptedToken = register("res_accepted", "res_accepted@example.com");
        String declinedToken = register("res_declined", "res_declined@example.com");
        String leftPendingToken = register("res_left", "res_left@example.com");
        rest.exchange(
                "/users/me", HttpMethod.PATCH, new HttpEntity<>(Map.of("isPrivate", true), bearer(ownerToken)), Map.class);

        StompSession ownerSession = connect(ownerToken);
        BlockingQueue<Map> ownerNotifications = new ArrayBlockingQueue<>(10);
        ownerSession.subscribe("/user/queue/notifications", frameHandler(Map.class, ownerNotifications));
        for (String requester : List.of(acceptedToken, declinedToken, leftPendingToken)) {
            rest.exchange("/users/res_owner/follow", HttpMethod.POST, new HttpEntity<>(null, bearer(requester)), Map.class);
        }
        for (int i = 0; i < 3; i++) {
            assertThat(ownerNotifications.poll(10, TimeUnit.SECONDS)).isNotNull();
        }
        assertThat(notificationTypes(ownerToken)).hasSize(3);

        rest.exchange(
                "/users/res_accepted/follow/accept", HttpMethod.POST, new HttpEntity<>(null, bearer(ownerToken)), Map.class);
        assertThat(notificationTypes(ownerToken))
                .as("accepting resolves that request, so its notification must not linger")
                .hasSize(2);

        rest.exchange(
                "/users/res_declined/follow/reject", HttpMethod.DELETE, new HttpEntity<>(null, bearer(ownerToken)), Void.class);
        assertThat(notificationTypes(ownerToken))
                .as("declining resolves that request, so its notification must not linger either")
                .hasSize(1);

        rest.exchange(
                "/users/me", HttpMethod.PATCH, new HttpEntity<>(Map.of("isPrivate", false), bearer(ownerToken)), Map.class);
        assertThat(notificationTypes(ownerToken))
                .as("going public auto-accepts whatever was still pending")
                .isEmpty();

        ownerSession.disconnect();
    }

    // The notification row is written asynchronously (event -> Redis stream -> consumer), so a
    // cancel can win the race and leave nothing to delete — the late event must then be dropped
    // rather than creating a notification for a request that no longer exists. The race itself can't
    // be forced deterministically over HTTP, so this feeds the consumer the event directly.
    @Test
    void aLateEventForAWithdrawnRequestIsDroppedAndAReplayedOneIsNotDuplicated() throws Exception {
        String ownerToken = register("late_owner", "late_owner@example.com");
        String requesterToken = register("late_requester", "late_requester@example.com");
        rest.exchange(
                "/users/me", HttpMethod.PATCH, new HttpEntity<>(Map.of("isPrivate", true), bearer(ownerToken)), Map.class);
        long ownerId = ((Number) profileOf("late_owner", ownerToken).get("id")).longValue();
        long requesterId = ((Number) profileOf("late_requester", requesterToken).get("id")).longValue();

        notificationConsumer.onMessage(followRequestEvent(ownerId, requesterId));
        assertThat(notificationTypes(ownerToken))
                .as("no request is pending, so the late event must not create a notification")
                .isEmpty();

        rest.exchange(
                "/users/late_owner/follow", HttpMethod.POST, new HttpEntity<>(null, bearer(requesterToken)), Map.class);
        awaitNotificationCount(ownerToken, 1);
        notificationConsumer.onMessage(followRequestEvent(ownerId, requesterId));
        assertThat(notificationTypes(ownerToken))
                .as("a redelivered/duplicate event must not add a second notification for the same request")
                .containsExactly("FOLLOW_REQUEST");
    }

    // Guards the failure this class of bug produced before: a NotificationType added to the enum
    // without widening the table's CHECK constraint / column length made every insert of it fail
    // silently inside the stream consumer. Every value must be persistable, whatever it's called.
    @Test
    void everyNotificationTypeFitsTheNotificationsTable() {
        String recipientToken = register("enum_recipient", "enum_recipient@example.com");
        register("enum_actor", "enum_actor@example.com");
        User recipient = userRepository.findByUsername("enum_recipient").orElseThrow();
        User actor = userRepository.findByUsername("enum_actor").orElseThrow();

        for (NotificationType type : NotificationType.values()) {
            Notification notification = new Notification();
            notification.setRecipient(recipient);
            notification.setActor(actor);
            notification.setType(type);
            notification.setTargetType("USER");
            notification.setTargetId(actor.getId());
            notification.setCreatedAt(Instant.now());
            notificationRepository.saveAndFlush(notification);
        }

        assertThat(notificationTypes(recipientToken))
                .containsExactlyInAnyOrderElementsOf(
                        java.util.Arrays.stream(NotificationType.values()).map(Enum::name).toList());
    }

    private MapRecord<String, String, String> followRequestEvent(long recipientId, long actorId) {
        return MapRecord.create(
                        "notifications-test",
                        Map.of(
                                "recipientId", String.valueOf(recipientId),
                                "actorId", String.valueOf(actorId),
                                "type", NotificationType.FOLLOW_REQUEST.name(),
                                "targetType", "USER",
                                "targetId", String.valueOf(actorId)))
                .withId(RecordId.of("0-1"));
    }

    private Map profileOf(String username, String viewerToken) {
        return rest.exchange("/users/" + username, HttpMethod.GET, new HttpEntity<>(bearer(viewerToken)), Map.class)
                .getBody();
    }

    private void awaitNotificationCount(String token, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (notificationTypes(token).size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(notificationTypes(token)).hasSize(expected);
    }

    private List<String> notificationTypes(String token) {
        ResponseEntity<Map> response =
                rest.exchange("/notifications", HttpMethod.GET, new HttpEntity<>(bearer(token)), Map.class);
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
        return items.stream().map(n -> (String) n.get("type")).toList();
    }

    private StompSession connect(String token) throws Exception {
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        WebSocketStompClient stompClient = new WebSocketStompClient(new SockJsClient(transports));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        return stompClient
                .connectAsync(
                        "ws://localhost:" + port + "/api/v1/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);
    }

    private <T> StompFrameHandler frameHandler(Class<T> type, BlockingQueue<T> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return type;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((T) payload);
            }
        };
    }

    private Number createPhotoPost(String token, String caption) {
        Map<String, Object> media = Map.of("url", uploadUrl(token, "image/jpeg"), "width", 800, "height", 600);
        ResponseEntity<Map> response = rest.exchange(
                "/posts",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("caption", caption, "media", List.of(media)), bearer(token)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Number) response.getBody().get("id");
    }

    // isOwnedUrl now checks the uploader's own id is embedded in the object key, not just the
    // bucket prefix — a hand-built fake URL no longer passes, so this goes through the real
    // presigned-upload endpoint to get a URL scoped to the given token's user.
    private String uploadUrl(String token, String contentType) {
        ResponseEntity<Map> response = rest.exchange(
                "/posts/upload-url",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("contentType", contentType), bearer(token)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("publicUrl");
    }

    private String register(String username, String email) {
        Map<String, Object> body = Map.of("username", username, "email", email, "password", "password123");
        ResponseEntity<Map> response = rest.postForEntity("/auth/register", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("accessToken");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
