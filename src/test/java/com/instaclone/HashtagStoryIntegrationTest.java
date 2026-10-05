package com.instaclone;

import static org.assertj.core.api.Assertions.assertThat;

import com.instaclone.auth.AuthRateLimitFilter;
import java.time.Instant;
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
 * Phase 4's "you'll know it works when": ... a story posted with a short test expiry ... actually
 * disappears from the feed after that window. Hashtag parsing/browsing is covered too, including
 * the same "private accounts never leak into a discovery surface" rule explore/search both apply.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = AuthRateLimitFilter.ENABLED_PROPERTY + "=false")
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class HashtagStoryIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void hashtagsAreParsedAndBrowsableButRespectPrivacy() {
        String alice = register("tag_alice", "tag_alice@example.com");
        String privateUser = register("tag_private", "tag_private@example.com");
        String viewer = register("tag_viewer", "tag_viewer@example.com");

        rest.exchange(
                "/users/me", HttpMethod.PATCH, new HttpEntity<>(Map.of("isPrivate", true), bearer(privateUser)), Map.class);

        ResponseEntity<Map> postResponse = createPhotoPost(alice, "loving this #Sunset and #vacation vibe");
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        List<String> hashtags = (List<String>) postResponse.getBody().get("hashtags");
        assertThat(hashtags).containsExactlyInAnyOrder("sunset", "vacation");

        ResponseEntity<Map> privatePostResponse = createPhotoPost(privateUser, "my own #sunset");
        Number privatePostId = (Number) privatePostResponse.getBody().get("id");

        ResponseEntity<Map> browseResponse =
                rest.exchange("/hashtags/sunset/posts", HttpMethod.GET, new HttpEntity<>(bearer(viewer)), Map.class);
        assertThat(browseResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = (List<Map<String, Object>>) browseResponse.getBody().get("items");
        List<Long> ids = items.stream().map(i -> ((Number) i.get("id")).longValue()).toList();

        assertThat(ids).contains(((Number) postResponse.getBody().get("id")).longValue());
        assertThat(ids).doesNotContain(privatePostId.longValue());
    }

    @Test
    void storyDisappearsAfterItsExpiryWindow() throws InterruptedException {
        String alice = register("story_alice", "story_alice@example.com");
        String bob = register("story_bob", "story_bob@example.com");
        rest.exchange("/users/story_alice/follow", HttpMethod.POST, new HttpEntity<>(null, bearer(bob)), Map.class);

        ResponseEntity<Map> createResponse = rest.exchange(
                "/stories",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("mediaUrl", uploadUrl(alice, "image/jpeg"), "expiresInSeconds", 2), bearer(alice)),
                Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number storyId = (Number) createResponse.getBody().get("id");

        assertThat(storyIdsInFeed(bob)).as("story should be visible immediately after posting").contains(storyId.longValue());

        Instant deadline = Instant.now().plusSeconds(10);
        boolean disappeared = false;
        while (Instant.now().isBefore(deadline)) {
            if (!storyIdsInFeed(bob).contains(storyId.longValue())) {
                disappeared = true;
                break;
            }
            Thread.sleep(500);
        }
        assertThat(disappeared).as("story should disappear from the feed once its expiry window passes").isTrue();
    }

    private List<Long> storyIdsInFeed(String viewerToken) {
        ResponseEntity<Map> response =
                rest.exchange("/stories/feed", HttpMethod.GET, new HttpEntity<>(bearer(viewerToken)), Map.class);
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
        return items.stream().map(i -> ((Number) i.get("id")).longValue()).toList();
    }

    private ResponseEntity<Map> createPhotoPost(String token, String caption) {
        Map<String, Object> media = Map.of("url", uploadUrl(token, "image/jpeg"), "width", 800, "height", 600);
        return rest.exchange(
                "/posts",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("caption", caption, "media", List.of(media)), bearer(token)),
                Map.class);
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
