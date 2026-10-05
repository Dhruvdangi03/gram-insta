package com.instaclone.post;

import com.instaclone.common.CursorPage;
import com.instaclone.common.PageParams;
import com.instaclone.common.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PostController {

    private final PostService postService;
    private final StorageService storageService;

    public PostController(PostService postService, StorageService storageService) {
        this.postService = postService;
        this.storageService = storageService;
    }

    @PostMapping("/posts/upload-url")
    public PresignedUploadResponse createUploadUrl(
            @Valid @RequestBody CreateUploadUrlRequest request, @AuthenticationPrincipal Jwt jwt) {
        return storageService.createUploadUrl(SecurityUtils.currentUserId(jwt), request.contentType());
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse createPost(@Valid @RequestBody CreatePostRequest request, @AuthenticationPrincipal Jwt jwt) {
        return postService.createPost(SecurityUtils.currentUserId(jwt), request);
    }

    @GetMapping("/posts/{id}")
    public PostResponse getPost(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return postService.getPost(id, SecurityUtils.currentUserId(jwt));
    }

    @PatchMapping("/posts/{id}")
    public PostResponse updatePost(
            @PathVariable Long id, @Valid @RequestBody UpdatePostRequest request, @AuthenticationPrincipal Jwt jwt) {
        return postService.updatePost(id, SecurityUtils.currentUserId(jwt), request);
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        postService.deletePost(id, SecurityUtils.currentUserId(jwt));
    }

    @GetMapping("/users/{username}/posts")
    public CursorPage<PostResponse> getUserPosts(
            @PathVariable String username,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        return postService.getUserPosts(username, SecurityUtils.currentUserId(jwt), cursor, PageParams.clamp(limit));
    }
}
