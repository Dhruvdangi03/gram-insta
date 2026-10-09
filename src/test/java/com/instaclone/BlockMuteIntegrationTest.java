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

/** Block (symmetric, severs follows, hides content and blocks DMs/follows) and mute (one-sided, feed-only). */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = AuthRateLimitFilter.ENABLED_PROPERTY + "=false")
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class BlockMuteIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void blockingSeversFollowsHidesContentAndPreventsContact() {
        String alice = register("blk_alice");
        String bob = register("blk_bob");
        Number postId = createPost(alice);
        post("/users/blk_alice/follow", bob);
        post("/users/blk_bob/follow", alice);
        assertThat(feedIds(bob)).contains(postId.longValue());

        assertThat(post("/users/blk_bob/block", alice).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post("/users/blk_bob/block", alice).getStatusCode()).as("idempotent").isEqualTo(HttpStatus.NO_CONTENT);

        // Both follow edges are gone.
        assertThat(profile("blk_alice", alice).get("followerCount")).isEqualTo(0);
        assertThat(profile("blk_alice", alice).get("followingCount")).isEqualTo(0);
        assertThat(feedIds(bob)).doesNotContain(postId.longValue());

        // The blocked user can no longer see the blocker's profile or content, or reach them.
        assertThat(get("/users/blk_alice", bob).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/posts/" + postId, bob).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/posts/" + postId + "/likes", bob).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/users/blk_alice/follow", bob).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/users/blk_bob/follow", alice).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/conversations", bob, Map.of("participantUsernames", List.of("blk_alice")))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Discovery surfaces run the exclusion-aware native queries; they must stay valid SQL.
        assertThat(get("/reels/feed", bob).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/explore", bob).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/stories/feed", bob).getStatusCode()).isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // The blocker still sees the blocked profile (to unblock) with the flag set, and lists them.
        Map blockedProfile = profile("blk_bob", alice);
        assertThat(blockedProfile.get("blockedByViewer")).isEqualTo(true);
        assertThat(searchUsernames("blk_", alice)).doesNotContain("blk_bob");
        assertThat(searchUsernames("blk_", bob)).doesNotContain("blk_alice");
        ResponseEntity<List> blocked = rest.exchange(
                "/users/me/blocked", HttpMethod.GET, new HttpEntity<>(bearer(alice)), List.class);
        assertThat(blocked.getBody()).hasSize(1);

        // Unblocking restores access (follows stay severed).
        assertThat(rest.exchange("/users/blk_bob/block", HttpMethod.DELETE, new HttpEntity<>(bearer(alice)), Void.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/users/blk_alice", bob).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/posts/" + postId, bob).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void cannotBlockOrMuteYourself() {
        String carol = register("self_carol");
        assertThat(post("/users/self_carol/block", carol).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/users/self_carol/mute", carol).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mutingHidesFromFeedOnlyAndIsUndoable() {
        String alice = register("mut_alice");
        String bob = register("mut_bob");
        Number postId = createPost(alice);
        post("/users/mut_alice/follow", bob);
        assertThat(feedIds(bob)).contains(postId.longValue());

        assertThat(post("/users/mut_alice/mute", bob).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(feedIds(bob)).doesNotContain(postId.longValue());
        // Mute is silent and narrow: still following, profile and post still reachable.
        assertThat(profile("mut_alice", bob).get("viewerRelationship")).isEqualTo("FOLLOWING");
        assertThat(profile("mut_alice", bob).get("mutedByViewer")).isEqualTo(true);
        assertThat(get("/posts/" + postId, bob).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.exchange("/users/me/muted", HttpMethod.GET, new HttpEntity<>(bearer(bob)), List.class)
                        .getBody())
                .hasSize(1);

        rest.exchange("/users/mut_alice/mute", HttpMethod.DELETE, new HttpEntity<>(bearer(bob)), Void.class);
        assertThat(feedIds(bob)).contains(postId.longValue());
    }

    private List<Long> feedIds(String token) {
        List<Map<String, Object>> items = (List<Map<String, Object>>)
                rest.exchange("/feed", HttpMethod.GET, new HttpEntity<>(bearer(token)), Map.class)
                        .getBody()
                        .get("items");
        return items.stream().map(i -> ((Number) i.get("id")).longValue()).toList();
    }

    private List<String> searchUsernames(String query, String token) {
        List<Map<String, Object>> results = rest.exchange(
                        "/search/users?q=" + query, HttpMethod.GET, new HttpEntity<>(bearer(token)), List.class)
                .getBody();
        return results.stream().map(r -> (String) r.get("username")).toList();
    }

    private Number createPost(String token) {
        Map<String, Object> media = Map.of("url", uploadUrl(token), "width", 800, "height", 600);
        ResponseEntity<Map> response = rest.exchange(
                "/posts",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("caption", "hi", "media", List.of(media)), bearer(token)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Number) response.getBody().get("id");
    }

    private String uploadUrl(String token) {
        return (String) rest.exchange(
                        "/posts/upload-url",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("contentType", "image/jpeg"), bearer(token)),
                        Map.class)
                .getBody()
                .get("publicUrl");
    }

    private ResponseEntity<Map> post(String path, String token) {
        return post(path, token, null);
    }

    private ResponseEntity<Map> post(String path, String token, Object body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), Map.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), Map.class);
    }

    private Map profile(String username, String token) {
        return get("/users/" + username, token).getBody();
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
