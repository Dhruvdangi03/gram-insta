package com.instaclone;

import static org.assertj.core.api.Assertions.assertThat;

import com.instaclone.auth.filter.AuthRateLimitFilter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Phase 2's own "you'll know it works when" bar: upload a short video, watch it transcode
 * asynchronously, see it playable in the reels feed a few seconds later — plus the explore feed's
 * privacy rule (a private account must never appear in it, regardless of like count).
 *
 * <p>Unlike CoreSocialGraphIntegrationTest's photo flow, this exercises a real presigned upload
 * against a real (Testcontainers) SeaweedFS instance, since the async transcode worker genuinely
 * downloads the source object — a fake URL wouldn't exercise the real pipeline.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = AuthRateLimitFilter.ENABLED_PROPERTY + "=false")
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class ReelsExploreIntegrationTest {

    // Not a Spring bean (unlike TestcontainersConfiguration's @ServiceConnection containers) — this
    // is the documented Testcontainers pattern for a service without first-class Spring support:
    // start it manually inside @DynamicPropertySource, before Spring reads its mapped port.
    private static final GenericContainer<?> STORAGE = new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.48"))
            .withCommand("server", "-dir=/data", "-s3", "-s3.port=8333")
            .withExposedPorts(8333);

    private static Path sampleVideo;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        STORAGE.start();
        String base = "http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(8333);
        registry.add("app.storage.endpoint", () -> base);
        registry.add("app.storage.public-base-url", () -> base);
    }

    @BeforeAll
    static void generateSampleVideo() throws IOException, InterruptedException {
        sampleVideo = Files.createTempFile("phase2-sample-", ".mp4");
        Process process = new ProcessBuilder(
                        "ffmpeg",
                        "-y",
                        "-f",
                        "lavfi",
                        "-i",
                        "testsrc=duration=2:size=320x240:rate=10",
                        "-pix_fmt",
                        "yuv420p",
                        sampleVideo.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        assertThat(finished).as("ffmpeg finished generating the sample video: %s", output).isTrue();
        assertThat(process.exitValue()).as("ffmpeg exit code, output: %s", output).isZero();
    }

    @AfterAll
    static void cleanup() throws IOException {
        if (sampleVideo != null) {
            Files.deleteIfExists(sampleVideo);
        }
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    void reelUploadTranscodesAsynchronouslyAndAppearsInFollowersFeed() throws IOException, InterruptedException {
        String authorToken = register("reel_author", "reel_author@example.com");
        String followerToken = register("reel_follower", "reel_follower@example.com");
        rest.exchange(
                "/users/reel_author/follow", HttpMethod.POST, new HttpEntity<>(null, bearer(followerToken)), Map.class);

        ResponseEntity<Map> uploadUrlResponse = rest.exchange(
                "/posts/upload-url",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("contentType", "video/mp4"), bearer(authorToken)),
                Map.class);
        assertThat(uploadUrlResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String uploadUrl = (String) uploadUrlResponse.getBody().get("uploadUrl");
        String publicUrl = (String) uploadUrlResponse.getBody().get("publicUrl");

        putToPresignedUrl(uploadUrl, sampleVideo);

        ResponseEntity<Map> reelResponse = rest.exchange(
                "/reels",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("caption", "phase 2 reel", "media", Map.of("url", publicUrl)), bearer(authorToken)),
                Map.class);
        assertThat(reelResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reelResponse.getBody().get("type")).isEqualTo("REEL");
        Number reelId = (Number) reelResponse.getBody().get("id");

        // Deterministic, not a race: this is the response body built (and serialized) inside the
        // same request that saved the media row as PENDING, before the after-commit publish can
        // have had any effect on it.
        List<Map<String, Object>> initialMedia = (List<Map<String, Object>>) reelResponse.getBody().get("media");
        assertThat(initialMedia.get(0).get("status")).isEqualTo("PENDING");

        Map<String, Object> readyMedia = awaitReadyMedia(reelId, authorToken);
        assertThat(readyMedia.get("status")).isEqualTo("READY");
        assertThat((String) readyMedia.get("url")).startsWith("http");
        assertThat(readyMedia.get("thumbnailUrl")).isNotNull();
        assertThat(readyMedia.get("width")).isNotNull();
        assertThat(readyMedia.get("height")).isNotNull();

        ResponseEntity<Map> reelsFeed =
                rest.exchange("/reels/feed", HttpMethod.GET, new HttpEntity<>(bearer(followerToken)), Map.class);
        assertThat(reelsFeed.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = (List<Map<String, Object>>) reelsFeed.getBody().get("items");
        assertThat(items.stream().map(i -> ((Number) i.get("id")).longValue())).contains(reelId.longValue());
    }

    @Test
    void explorePrivacyExcludesPrivateAccountsButShowsPublicOnes() {
        String viewerToken = register("explore_viewer", "explore_viewer@example.com");
        String privateToken = register("explore_private", "explore_private@example.com");
        String publicToken = register("explore_public", "explore_public@example.com");

        rest.exchange(
                "/users/me", HttpMethod.PATCH, new HttpEntity<>(Map.of("isPrivate", true), bearer(privateToken)), Map.class);

        Number privatePostId = createPhotoPost(privateToken, "private post");
        Number publicPostId = createPhotoPost(publicToken, "public post");

        ResponseEntity<Map> exploreResponse =
                rest.exchange("/explore", HttpMethod.GET, new HttpEntity<>(bearer(viewerToken)), Map.class);
        assertThat(exploreResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = (List<Map<String, Object>>) exploreResponse.getBody().get("items");
        List<Long> ids = items.stream().map(i -> ((Number) i.get("id")).longValue()).toList();

        assertThat(ids).contains(publicPostId.longValue());
        assertThat(ids).doesNotContain(privatePostId.longValue());
    }

    private Number createPhotoPost(String token, String caption) {
        // No real bytes needed — PostService.createPost only checks the url prefix, it never
        // fetches the object (unlike the reel/transcode path above). isOwnedUrl does check the
        // uploader's own id is embedded in the key though, so this goes through the real
        // presigned-upload endpoint rather than hand-building a URL.
        Map<String, Object> media = Map.of("url", uploadUrl(token, "image/jpeg"), "width", 800, "height", 600);
        ResponseEntity<Map> response = rest.exchange(
                "/posts",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("caption", caption, "media", List.of(media)), bearer(token)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Number) response.getBody().get("id");
    }

    private String uploadUrl(String token, String contentType) {
        ResponseEntity<Map> response = rest.exchange(
                "/posts/upload-url",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("contentType", contentType), bearer(token)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("publicUrl");
    }

    private Map<String, Object> awaitReadyMedia(Number postId, String token) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<Map> response =
                    rest.exchange("/posts/" + postId, HttpMethod.GET, new HttpEntity<>(bearer(token)), Map.class);
            List<Map<String, Object>> media = (List<Map<String, Object>>) response.getBody().get("media");
            Map<String, Object> first = media.get(0);
            String status = (String) first.get("status");
            if (!"PENDING".equals(status) && !"PROCESSING".equals(status)) {
                return first;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Reel media did not finish processing within 30s");
    }

    private void putToPresignedUrl(String url, Path file) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "video/mp4")
                .PUT(HttpRequest.BodyPublishers.ofFile(file))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("presigned PUT response: %s", response.body()).isBetween(200, 299);
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
