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

/** Hashtag indexing/discovery and @mention notifications, end to end. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = AuthRateLimitFilter.ENABLED_PROPERTY + "=false")
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class MentionHashtagIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void hashtagsAreIndexedOnCreateAndReindexedOnEdit() {
        String alice = register("ht_alice");
        String bob = register("ht_bob");
        Number first = createPost(alice, "Golden hour #SunsetGlow #ht_beach");
        Number second = createPost(alice, "again #sunsetglow");

        assertThat(hashtagPostIds("sunsetglow", bob)).containsExactly(second.longValue(), first.longValue());
        assertThat(get("/hashtags/SunsetGlow", bob).getBody().get("postCount")).isEqualTo(2);
        assertThat(get("/hashtags/ht_beach", bob).getBody().get("postCount")).isEqualTo(1);

        // Editing the caption replaces the post's tags.
        patch("/posts/" + first, alice, Map.of("caption", "now about #ht_mountains"));
        assertThat(hashtagPostIds("sunsetglow", bob)).containsExactly(second.longValue());
        assertThat(hashtagPostIds("ht_mountains", bob)).containsExactly(first.longValue());
        assertThat(hashtagPostIds("ht_beach", bob)).isEmpty();

        // Deleting a post removes it from the tag.
        rest.exchange("/posts/" + second, HttpMethod.DELETE, new HttpEntity<>(bearer(alice)), Void.class);
        assertThat(hashtagPostIds("sunsetglow", bob)).isEmpty();

        // Prefix search, most-used first, '#' tolerated, wildcards literal.
        assertThat(searchTags("ht_m", bob)).contains("ht_mountains");
        assertThat(searchTags("#ht_m", bob)).contains("ht_mountains");
        assertThat(searchTags("%", bob)).isEmpty();
        assertThat(get("/hashtags/bad-tag!", bob).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void hashtagPagesHidePrivateAccountsAndBlockedUsers() {
        String pub = register("hp_public");
        String priv = register("hp_private");
        String viewer = register("hp_viewer");
        String blocked = register("hp_blocked");
        rest.exchange("/users/me", HttpMethod.PATCH, new HttpEntity<>(Map.of("isPrivate", true), bearer(priv)), Map.class);

        Number publicPost = createPost(pub, "#hp_discover public");
        createPost(priv, "#hp_discover private");
        createPost(blocked, "#hp_discover blocked author");
        post("/users/hp_blocked/block", viewer, null);

        assertThat(hashtagPostIds("hp_discover", viewer)).containsExactly(publicPost.longValue());
        assertThat(get("/hashtags/hp_discover", viewer).getBody().get("postCount")).isEqualTo(1);
    }

    @Test
    void mentionsNotifyOnlyPeopleWhoShouldBeNotified() throws InterruptedException {
        String author = register("mn_author");
        String blockedUser = register("mn_blocked");
        String stranger = register("mn_stranger");
        String target = register("mn_target");
        post("/users/mn_author/block", author, null); // self-block is rejected; harmless
        post("/users/mn_blocked/block", author, null);

        // Mention order puts the notification that SHOULD arrive last: the stream is consumed in
        // order, so once it shows up every earlier (suppressed) one has already been processed.
        createPost(author, "hi @mn_author @mn_blocked @nobody_here_xx @mn_target");

        awaitNotificationTypes(target, List.of("MENTION_POST"));
        assertThat(notificationTypes(author)).as("self-mention is ignored").isEmpty();
        assertThat(notificationTypes(blockedUser)).as("blocked user isn't notified").isEmpty();
        assertThat(notificationTypes(stranger)).isEmpty();
    }

    @Test
    void editingACaptionNotifiesOnlyNewMentions() throws InterruptedException {
        String author = register("ed_author");
        String first = register("ed_first");
        String second = register("ed_second");
        Number postId = createPost(author, "with @ed_first");
        awaitNotificationTypes(first, List.of("MENTION_POST"));

        patch("/posts/" + postId, author, Map.of("caption", "with @ed_first and @ed_second"));
        awaitNotificationTypes(second, List.of("MENTION_POST"));
        assertThat(notificationTypes(first)).as("already-mentioned user isn't notified again").hasSize(1);
    }

    @Test
    void commentMentionsNotifyOnceEvenIfAlsoOwnerOrReplyTarget() throws InterruptedException {
        String owner = register("cm_owner");
        String commenter = register("cm_commenter");
        String other = register("cm_other");
        Number postId = createPost(owner, "a post");

        post("/posts/" + postId + "/comments", commenter, Map.of("text", "@cm_owner look, and @cm_other too"));

        awaitNotificationTypes(other, List.of("MENTION_COMMENT"));
        // The owner gets the normal COMMENT notification and no duplicate mention for the same comment.
        awaitNotificationTypes(owner, List.of("COMMENT"));
    }

    @Test
    void mentionsOnAPrivateAccountsPostDoNotReachNonFollowers() throws InterruptedException {
        String owner = register("pm_owner");
        String follower = register("pm_follower");
        String outsider = register("pm_outsider");
        rest.exchange("/users/me", HttpMethod.PATCH, new HttpEntity<>(Map.of("isPrivate", true), bearer(owner)), Map.class);
        post("/users/pm_owner/follow", follower, null);
        post("/users/pm_follower/follow/accept", owner, null); // no-op if wrong direction

        // Outsider is mentioned first; follower (who can see the post) last.
        rest.exchange("/users/pm_owner/follow/accept", HttpMethod.POST, new HttpEntity<>(null, bearer(owner)), Map.class);
        post("/users/pm_follower/follow/accept", owner, null);
        createPost(owner, "private hello @pm_outsider @pm_follower");

        awaitNotificationTypes(follower, List.of("MENTION_POST"));
        assertThat(notificationTypes(outsider)).isEmpty();
    }

    private List<Long> hashtagPostIds(String tag, String token) {
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) get("/hashtags/" + tag + "/posts", token).getBody().get("items");
        return items.stream().map(i -> ((Number) i.get("id")).longValue()).toList();
    }

    private List<String> searchTags(String q, String token) {
        // URI template variable so the client encodes '#' and '%' itself.
        List<Map<String, Object>> rows = rest.exchange(
                        "/search/hashtags?q={q}", HttpMethod.GET, new HttpEntity<>(bearer(token)), List.class, q)
                .getBody();
        return rows.stream().map(r -> (String) r.get("tag")).toList();
    }

    private void awaitNotificationTypes(String token, List<String> expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!notificationTypes(token).containsAll(expected) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(notificationTypes(token)).containsAll(expected);
    }

    private List<String> notificationTypes(String token) {
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) get("/notifications", token).getBody().get("items");
        return items.stream().map(n -> (String) n.get("type")).toList();
    }

    private Number createPost(String token, String caption) {
        String url = (String) post("/posts/upload-url", token, Map.of("contentType", "image/jpeg")).getBody().get("publicUrl");
        Map<String, Object> media = Map.of("url", url, "width", 800, "height", 600);
        ResponseEntity<Map> response = post("/posts", token, Map.of("caption", caption, "media", List.of(media)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Number) response.getBody().get("id");
    }

    private ResponseEntity<Map> post(String path, String token, Object body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), Map.class);
    }

    private ResponseEntity<Map> patch(String path, String token, Object body) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, bearer(token)), Map.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), Map.class);
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
