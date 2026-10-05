package com.instaclone.social.saved;

import com.instaclone.common.CursorPage;
import com.instaclone.common.PageParams;
import com.instaclone.common.SecurityUtils;
import com.instaclone.post.PostResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SavedPostController {

    private final SavedPostService savedPostService;

    public SavedPostController(SavedPostService savedPostService) {
        this.savedPostService = savedPostService;
    }

    @PostMapping("/posts/{id}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        savedPostService.savePost(SecurityUtils.currentUserId(jwt), id);
    }

    @DeleteMapping("/posts/{id}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        savedPostService.unsavePost(SecurityUtils.currentUserId(jwt), id);
    }

    @GetMapping("/users/me/saved-posts")
    public CursorPage<PostResponse> getSavedPosts(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        return savedPostService.getSavedPosts(SecurityUtils.currentUserId(jwt), cursor, PageParams.clamp(limit));
    }
}
