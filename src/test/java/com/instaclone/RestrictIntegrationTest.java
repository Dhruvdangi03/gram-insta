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
class RestrictIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void restrictedUsersCommentIsHiddenUntilApproved() {
        String owner = register("rs_owner");
        String bob = register("rs_bob");
        String carol = register("rs_carol");
        Number postId = createPost(owner);

        assertThat(call("/users/rs_bob/restrict", HttpMethod.POST, owner).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        // idempotent; and the flag is only visible to the restrictor
        assertThat(call("/users/rs_bob/restrict", HttpMethod.POST, owner).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call("/users/rs_bob", HttpMethod.GET, owner).getBody().get("restrictedByViewer")).isEqualTo(true);
        assertThat(call("/users/rs_owner", HttpMethod.GET, bob).getBody().get("restrictedByViewer")).isEqualTo(false);

        ResponseEntity<Map> created = call("/posts/" + postId + "/comments", HttpMethod.POST, bob, Map.of("text", "hello"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number commentId = (Number) created.getBody().get("id");
        // the author's own view looks perfectly normal
        assertThat(created.getBody().get("pendingApproval")).isEqualTo(false);

        assertThat(commentIds(postId, bob)).contains(commentId.longValue());
        assertThat(commentIds(postId, owner)).contains(commentId.longValue());
        assertThat(commentIds(postId, carol)).doesNotContain(commentId.longValue());
        assertThat(pending(postId, owner, commentId)).isTrue();
        assertThat(pending(postId, bob, commentId)).isFalse();

        // hidden comments aren't counted and can't be liked by others
        assertThat(call("/posts/" + postId, HttpMethod.GET, carol).getBody().get("commentCount")).isEqualTo(0);
        assertThat(call("/comments/" + commentId + "/likes", HttpMethod.POST, carol).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // only the owner can approve
        assertThat(call("/comments/" + commentId + "/approve", HttpMethod.POST, carol).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call("/comments/" + commentId + "/approve", HttpMethod.POST, owner).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(commentIds(postId, carol)).contains(commentId.longValue());
        assertThat(call("/posts/" + postId, HttpMethod.GET, carol).getBody().get("commentCount")).isEqualTo(1);
    }

    @Test
    void ownerCanDismissPendingCommentAndCommentsBeforeRestrictStayPublic() {
        String owner = register("rd_owner");
        String bob = register("rd_bob");
        String carol = register("rd_carol");
        Number postId = createPost(owner);

        Number before = (Number) call("/posts/" + postId + "/comments", HttpMethod.POST, bob, Map.of("text", "early"))
                .getBody().get("id");
        call("/users/rd_bob/restrict", HttpMethod.POST, owner);
        assertThat(commentIds(postId, carol)).contains(before.longValue());

        Number pendingId = (Number) call("/posts/" + postId + "/comments", HttpMethod.POST, bob, Map.of("text", "later"))
                .getBody().get("id");
        assertThat(call("/comments/" + pendingId, HttpMethod.DELETE, owner).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(commentIds(postId, bob)).doesNotContain(pendingId.longValue());
        // count unaffected by deleting an unapproved comment
        assertThat(call("/posts/" + postId, HttpMethod.GET, carol).getBody().get("commentCount")).isEqualTo(1);
        // but the owner may not delete an approved comment by someone else
        assertThat(call("/comments/" + before, HttpMethod.DELETE, owner).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void restrictedUsersMessagesGoToRequestsAndSenderIsNotCapped() {
        String alice = register("rm_alice");
        String bob = register("rm_bob");
        call("/users/rm_bob/follow", HttpMethod.POST, alice); // alice follows bob
        call("/users/rm_alice/follow", HttpMethod.POST, bob); // bob follows alice -> normally straight to inbox

        Number id = (Number) call("/conversations", HttpMethod.POST, bob, Map.of("participantUsernames", List.of("rm_alice")))
                .getBody().get("id");
        assertThat(conversationIds("/conversations", alice)).contains(id.longValue());

        // alice restricts bob -> the existing chat moves to her requests
        call("/users/rm_bob/restrict", HttpMethod.POST, alice);
        assertThat(conversationIds("/conversations", alice)).doesNotContain(id.longValue());
        assertThat(conversationIds("/conversations/requests", alice)).contains(id.longValue());

        // bob's side still looks normal and he can keep sending (no cap)
        List<Map<String, Object>> bobConvos = (List<Map<String, Object>>) call("/conversations", HttpMethod.GET, bob).getBody().get("items");
        assertThat(bobConvos).anyMatch(c -> ((Number) c.get("id")).longValue() == id.longValue() && "ACCEPTED".equals(c.get("status")));
        assertThat(send(id, bob, "one").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(send(id, bob, "two").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // alice replying doesn't lift the restriction: bob's next message re-requests
        assertThat(send(id, alice, "hi").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        send(id, bob, "three");
        assertThat(conversationIds("/conversations/requests", alice)).contains(id.longValue());

        // unrestrict -> back to the inbox (alice follows bob)
        call("/users/rm_bob/restrict", HttpMethod.DELETE, alice);
        assertThat(conversationIds("/conversations", alice)).contains(id.longValue());
        assertThat(conversationIds("/conversations/requests", alice)).doesNotContain(id.longValue());
    }

    @Test
    void cannotRestrictYourselfAndListShowsRestricted() {
        String alice = register("rl_alice");
        register("rl_bob");
        assertThat(call("/users/rl_alice/restrict", HttpMethod.POST, alice).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        call("/users/rl_bob/restrict", HttpMethod.POST, alice);
        ResponseEntity<List> list = rest.exchange("/users/me/restricted", HttpMethod.GET, new HttpEntity<>(bearer(alice)), List.class);
        assertThat(list.getBody()).hasSize(1);
    }

    private List<Long> commentIds(Number postId, String token) {
        ResponseEntity<Map> page = call("/posts/" + postId + "/comments", HttpMethod.GET, token);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((List<Map<String, Object>>) page.getBody().get("items"))
                .stream().map(m -> ((Number) m.get("id")).longValue()).toList();
    }

    private boolean pending(Number postId, String token, Number commentId) {
        return ((List<Map<String, Object>>) call("/posts/" + postId + "/comments", HttpMethod.GET, token).getBody().get("items"))
                .stream()
                .filter(m -> ((Number) m.get("id")).longValue() == commentId.longValue())
                .anyMatch(m -> Boolean.TRUE.equals(m.get("pendingApproval")));
    }

    private List<Long> conversationIds(String path, String token) {
        return ((List<Map<String, Object>>) call(path, HttpMethod.GET, token).getBody().get("items"))
                .stream().map(m -> ((Number) m.get("id")).longValue()).toList();
    }

    private ResponseEntity<Map> send(Number id, String token, String content) {
        return call("/conversations/" + id + "/messages", HttpMethod.POST, token, Map.of("content", content));
    }

    private ResponseEntity<Map> call(String path, HttpMethod method, String token) {
        return rest.exchange(path, method, new HttpEntity<>(null, bearer(token)), Map.class);
    }

    private ResponseEntity<Map> call(String path, HttpMethod method, String token, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, bearer(token)), Map.class);
    }

    private Number createPost(String token) {
        String url = (String) call("/posts/upload-url", HttpMethod.POST, token, Map.of("contentType", "image/jpeg"))
                .getBody().get("publicUrl");
        Map<String, Object> media = Map.of("url", url, "width", 800, "height", 600);
        ResponseEntity<Map> response = call("/posts", HttpMethod.POST, token, Map.of("caption", "hi", "media", List.of(media)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Number) response.getBody().get("id");
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
