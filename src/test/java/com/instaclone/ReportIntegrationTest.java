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

/** Reporting posts, comments and users: validation, visibility, self-report and idempotency rules. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = AuthRateLimitFilter.ENABLED_PROPERTY + "=false")
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class ReportIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void reportingPostCommentAndUser() {
        String alice = register("rep_alice");
        String bob = register("rep_bob");
        Number postId = createPost(alice);
        Number commentId = comment(alice, postId, "buy followers here");
        Map<String, Object> spam = Map.of("reason", "SPAM", "details", "  link farm  ");

        ResponseEntity<Map> postReport = post("/posts/" + postId + "/report", bob, spam);
        assertThat(postReport.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postReport.getBody().get("status")).isEqualTo("OPEN");

        assertThat(post("/comments/" + commentId + "/report", bob, spam).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(post("/users/rep_alice/report", bob, Map.of("reason", "SCAM")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // Reporting changes nothing visible: the post is still there for everyone.
        assertThat(get("/posts/" + postId, bob).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void reportingTwiceReturnsTheOriginalReport() {
        String alice = register("dup_alice");
        String bob = register("dup_bob");
        Number postId = createPost(alice);

        ResponseEntity<Map> first = post("/posts/" + postId + "/report", bob, Map.of("reason", "SPAM"));
        ResponseEntity<Map> second = post("/posts/" + postId + "/report", bob, Map.of("reason", "HATE"));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().get("id")).isEqualTo(first.getBody().get("id"));
    }

    @Test
    void rejectsInvalidSelfAndUnknownTargets() {
        String alice = register("val_alice");
        String bob = register("val_bob");
        Number postId = createPost(alice);
        Number commentId = comment(alice, postId, "mine");

        assertThat(post("/posts/" + postId + "/report", bob, Map.of("reason", "NOT_A_REASON")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/posts/" + postId + "/report", bob, Map.of()).getStatusCode())
                .as("reason is required")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/posts/" + postId + "/report", alice, Map.of("reason", "SPAM")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/comments/" + commentId + "/report", alice, Map.of("reason", "SPAM")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/users/val_alice/report", alice, Map.of("reason", "SPAM")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/posts/999999/report", bob, Map.of("reason", "SPAM")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/users/nobody_here/report", bob, Map.of("reason", "SPAM")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cannotReportWhatYouCannotSee() {
        String owner = register("priv_owner");
        String stranger = register("priv_stranger");
        Number postId = createPost(owner);
        rest.exchange("/users/me", HttpMethod.PATCH, new HttpEntity<>(Map.of("isPrivate", true), bearer(owner)), Map.class);

        assertThat(post("/posts/" + postId + "/report", stranger, Map.of("reason", "SPAM")).getStatusCode())
                .as("a private account's post is reported as not found, not leaked")
                .isEqualTo(HttpStatus.NOT_FOUND);

        // A blocked reporter can't find the blocker by reporting them; the blocker can still report.
        String harasser = register("blk_harasser");
        String victim = register("blk_victim");
        post("/users/blk_harasser/block", victim, null);
        assertThat(post("/users/blk_victim/report", harasser, Map.of("reason", "SPAM")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/users/blk_harasser/report", victim, Map.of("reason", "HARASSMENT")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    private Number comment(String token, Number postId, String text) {
        return (Number) post("/posts/" + postId + "/comments", token, Map.of("text", text)).getBody().get("id");
    }

    private Number createPost(String token) {
        String url = (String) post("/posts/upload-url", token, Map.of("contentType", "image/jpeg")).getBody().get("publicUrl");
        Map<String, Object> media = Map.of("url", url, "width", 800, "height", 600);
        ResponseEntity<Map> response = post("/posts", token, Map.of("caption", "hi", "media", List.of(media)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Number) response.getBody().get("id");
    }

    private ResponseEntity<Map> post(String path, String token, Object body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), Map.class);
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
