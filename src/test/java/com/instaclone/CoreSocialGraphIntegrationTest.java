package com.instaclone;

import static org.assertj.core.api.Assertions.assertThat;

import com.instaclone.auth.AuthRateLimitFilter;
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

/**
 * Exercises the exact Phase 1 "you'll know it works when" flow over real HTTP against a real
 * (Testcontainers) Postgres and Redis: register two accounts, one posts, the other follows and
 * sees it in their feed, likes it, comments on it, and the denormalized counts update.
 *
 * <p>TestRestTemplate's root URI already includes server.servlet.context-path (/api/v1), so
 * paths below are relative to that — not repeating the prefix.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = AuthRateLimitFilter.ENABLED_PROPERTY + "=false")
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class CoreSocialGraphIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void registerPostFollowLikeCommentFeedFlow() {
        String aliceToken = register("alice", "alice@example.com");
        String bobToken = register("bob", "bob@example.com");

        Map<String, Object> media = Map.of("url", uploadUrl(aliceToken, "image/jpeg"), "width", 800, "height", 600);
        Map<String, Object> createPost = Map.of("caption", "hello world", "media", List.of(media));
        ResponseEntity<Map> postResponse =
                rest.exchange("/posts", HttpMethod.POST, new HttpEntity<>(createPost, bearer(aliceToken)), Map.class);
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number postId = (Number) postResponse.getBody().get("id");

        ResponseEntity<Map> followResponse = rest.exchange(
                "/users/alice/follow", HttpMethod.POST, new HttpEntity<>(null, bearer(bobToken)), Map.class);
        assertThat(followResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(followResponse.getBody().get("status")).isEqualTo("ACCEPTED");

        ResponseEntity<Map> feedResponse =
                rest.exchange("/feed", HttpMethod.GET, new HttpEntity<>(bearer(bobToken)), Map.class);
        assertThat(feedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = (List<Map<String, Object>>) feedResponse.getBody().get("items");
        assertThat(items).hasSize(1);
        assertThat(((Number) items.get(0).get("id")).longValue()).isEqualTo(postId.longValue());

        rest.exchange(
                "/posts/" + postId + "/likes", HttpMethod.POST, new HttpEntity<>(null, bearer(bobToken)), Map.class);
        rest.exchange(
                "/posts/" + postId + "/comments",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("text", "nice!"), bearer(bobToken)),
                Map.class);

        ResponseEntity<Map> postDetail =
                rest.exchange("/posts/" + postId, HttpMethod.GET, new HttpEntity<>(bearer(aliceToken)), Map.class);
        assertThat(((Number) postDetail.getBody().get("likeCount")).intValue()).isEqualTo(1);
        assertThat(((Number) postDetail.getBody().get("commentCount")).intValue()).isEqualTo(1);
    }

    @Test
    void removingAFollowerSeversTheRelationshipAndRevokesPrivateAccess() {
        String ownerToken = register("rm_owner", "rm_owner@example.com");
        String fanToken = register("rm_fan", "rm_fan@example.com");
        rest.exchange(
                "/users/me", HttpMethod.PATCH, new HttpEntity<>(Map.of("isPrivate", true), bearer(ownerToken)), Map.class);

        rest.exchange("/users/rm_owner/follow", HttpMethod.POST, new HttpEntity<>(null, bearer(fanToken)), Map.class);
        rest.exchange(
                "/users/rm_fan/follow/accept", HttpMethod.POST, new HttpEntity<>(null, bearer(ownerToken)), Map.class);
        assertThat(profile("rm_owner", ownerToken).get("followerCount")).isEqualTo(1);
        assertThat(rest.exchange("/users/rm_owner/posts", HttpMethod.GET, new HttpEntity<>(bearer(fanToken)), Map.class)
                        .getStatusCode())
                .as("an accepted follower can see a private account's posts")
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<Void> removal = rest.exchange(
                "/users/rm_fan/follow/remove", HttpMethod.DELETE, new HttpEntity<>(null, bearer(ownerToken)), Void.class);
        assertThat(removal.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(profile("rm_owner", ownerToken).get("followerCount")).isEqualTo(0);
        assertThat(profile("rm_owner", fanToken).get("viewerRelationship")).isEqualTo("NOT_FOLLOWING");
        assertThat(rest.exchange("/users/rm_owner/posts", HttpMethod.GET, new HttpEntity<>(bearer(fanToken)), Map.class)
                        .getStatusCode())
                .as("a removed follower loses access to a private account")
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(rest.exchange(
                                "/users/rm_fan/follow/remove",
                                HttpMethod.DELETE,
                                new HttpEntity<>(null, bearer(ownerToken)),
                                Map.class)
                        .getStatusCode())
                .as("removing someone who isn't (or is no longer) a follower is a 404, not a silent success")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aPendingRequesterIsNotAFollowerToRemove() {
        String ownerToken = register("rm_pending_owner", "rm_pending_owner@example.com");
        String requesterToken = register("rm_pending_req", "rm_pending_req@example.com");
        rest.exchange(
                "/users/me", HttpMethod.PATCH, new HttpEntity<>(Map.of("isPrivate", true), bearer(ownerToken)), Map.class);
        rest.exchange(
                "/users/rm_pending_owner/follow", HttpMethod.POST, new HttpEntity<>(null, bearer(requesterToken)), Map.class);

        // A still-PENDING request is declined via /follow/reject; "remove follower" is only for accepted ones.
        assertThat(rest.exchange(
                                "/users/rm_pending_req/follow/remove",
                                HttpMethod.DELETE,
                                new HttpEntity<>(null, bearer(ownerToken)),
                                Map.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(profile("rm_pending_owner", requesterToken).get("viewerRelationship")).isEqualTo("REQUESTED");
    }

    @Test
    void removingOneFollowerLeavesOthersAndWorksOnPublicAccounts() {
        String ownerToken = register("pub_owner", "pub_owner@example.com");
        String removedToken = register("pub_removed", "pub_removed@example.com");
        String keptToken = register("pub_kept", "pub_kept@example.com");
        rest.exchange("/users/pub_owner/follow", HttpMethod.POST, new HttpEntity<>(null, bearer(removedToken)), Map.class);
        rest.exchange("/users/pub_owner/follow", HttpMethod.POST, new HttpEntity<>(null, bearer(keptToken)), Map.class);
        assertThat(profile("pub_owner", ownerToken).get("followerCount")).isEqualTo(2);

        assertThat(rest.exchange(
                                "/users/pub_removed/follow/remove",
                                HttpMethod.DELETE,
                                new HttpEntity<>(null, bearer(ownerToken)),
                                Void.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(profile("pub_owner", ownerToken).get("followerCount")).isEqualTo(1);
        assertThat(profile("pub_owner", removedToken).get("viewerRelationship")).isEqualTo("NOT_FOLLOWING");
        assertThat(profile("pub_owner", keptToken).get("viewerRelationship"))
                .as("removing one follower must not touch the others")
                .isEqualTo("FOLLOWING");

        // A public account can't lock anyone out — the removed user is free to follow again.
        ResponseEntity<Map> refollow = rest.exchange(
                "/users/pub_owner/follow", HttpMethod.POST, new HttpEntity<>(null, bearer(removedToken)), Map.class);
        assertThat(refollow.getBody().get("status")).isEqualTo("ACCEPTED");
    }

    private Map profile(String username, String viewerToken) {
        return rest.exchange("/users/" + username, HttpMethod.GET, new HttpEntity<>(bearer(viewerToken)), Map.class)
                .getBody();
    }

    private String register(String username, String email) {
        Map<String, Object> body = Map.of("username", username, "email", email, "password", "password123");
        ResponseEntity<Map> response = rest.postForEntity("/auth/register", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("accessToken");
    }

    // isOwnedUrl checks the uploader's own id is embedded in the object key, not just the bucket
    // prefix — a hand-built fake URL doesn't pass, so this goes through the real presigned-upload
    // endpoint to get a URL scoped to the given token's user.
    private String uploadUrl(String token, String contentType) {
        ResponseEntity<Map> response = rest.exchange(
                "/posts/upload-url",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("contentType", contentType), bearer(token)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("publicUrl");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
