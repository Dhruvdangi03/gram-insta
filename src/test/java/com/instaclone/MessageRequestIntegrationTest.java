package com.instaclone;

import static org.assertj.core.api.Assertions.assertThat;

import com.instaclone.auth.filter.AuthRateLimitFilter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = AuthRateLimitFilter.ENABLED_PROPERTY + "=false")
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class MessageRequestIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void messageFromNonFollowerIsARequestUntilAccepted() {
        String alice = register("req_alice");
        String bob = register("req_bob");

        Number id = startConversation(alice, "req_bob");
        assertThat(send(id, alice, "hi bob").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Sender may only send one message before acceptance.
        assertThat(send(id, alice, "hello??").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Bob: not in inbox, present in requests, counted, and the sender still sees her own chat.
        assertThat(ids(get("/conversations", bob))).doesNotContain(id.longValue());
        assertThat(ids(get("/conversations/requests", bob))).contains(id.longValue());
        assertThat(get("/conversations/requests/count", bob).getBody().get("count")).isEqualTo(1);
        assertThat(ids(get("/conversations", alice))).contains(id.longValue());
        assertThat(ids(get("/conversations/requests", alice))).doesNotContain(id.longValue());

        // The sender can't accept her own request.
        assertThat(post("/conversations/" + id + "/accept", alice).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(post("/conversations/" + id + "/accept", bob).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ids(get("/conversations", bob))).contains(id.longValue());
        assertThat(ids(get("/conversations/requests", bob))).doesNotContain(id.longValue());
        assertThat(send(id, alice, "thanks!").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void replyingAcceptsTheRequest() {
        String alice = register("rep_alice");
        String bob = register("rep_bob");
        Number id = startConversation(alice, "rep_bob");
        send(id, alice, "hi");

        assertThat(send(id, bob, "hey").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ids(get("/conversations", bob))).contains(id.longValue());
        assertThat(get("/conversations/requests/count", bob).getBody().get("count")).isEqualTo(0);
    }

    @Test
    void decliningDeletesTheRequest() {
        String alice = register("dec_alice");
        String bob = register("dec_bob");
        Number id = startConversation(alice, "dec_bob");
        send(id, alice, "hi");

        assertThat(rest.exchange("/conversations/" + id + "/request", HttpMethod.DELETE, new HttpEntity<>(bearer(bob)), Void.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(ids(get("/conversations/requests", bob))).doesNotContain(id.longValue());
        assertThat(ids(get("/conversations", alice))).doesNotContain(id.longValue());
    }

    @Test
    void followedSenderGoesStraightToInbox() {
        String alice = register("fol_alice");
        String bob = register("fol_bob");
        post("/users/fol_alice/follow", bob);

        Number id = startConversation(alice, "fol_bob");
        assertThat(ids(get("/conversations", bob))).contains(id.longValue());
        assertThat(get("/conversations/requests/count", bob).getBody().get("count")).isEqualTo(0);
        assertThat(send(id, alice, "one").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(send(id, alice, "two").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void blockedSenderRequestsAreHidden() {
        String alice = register("blr_alice");
        String bob = register("blr_bob");
        Number id = startConversation(alice, "blr_bob");
        send(id, alice, "hi");

        post("/users/blr_alice/block", bob);
        assertThat(ids(get("/conversations/requests", bob))).doesNotContain(id.longValue());
        assertThat(get("/conversations/requests/count", bob).getBody().get("count")).isEqualTo(0);
    }

    private Number startConversation(String token, String otherUsername) {
        ResponseEntity<Map> r = rest.exchange(
                "/conversations",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("participantUsernames", List.of(otherUsername)), bearer(token)),
                Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Number) r.getBody().get("id");
    }

    private ResponseEntity<Map> send(Number id, String token, String content) {
        return rest.exchange(
                "/conversations/" + id + "/messages",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("content", content), bearer(token)),
                Map.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), Map.class);
    }

    private ResponseEntity<Map> post(String path, String token) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(null, bearer(token)), Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Long> ids(ResponseEntity<Map> page) {
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((List<Map<String, Object>>) page.getBody().get("items"))
                .stream().map(m -> ((Number) m.get("id")).longValue()).toList();
    }

    private String register(String username) {
        Map<String, Object> body = Map.of("username", username, "email", username + "@example.com", "password", "password123");
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
