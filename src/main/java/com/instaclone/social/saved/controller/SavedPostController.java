package com.instaclone.social.saved.controller;

import com.instaclone.common.pagination.CursorPage;
import com.instaclone.common.pagination.PageParams;
import com.instaclone.common.util.SecurityUtils;
import com.instaclone.post.dto.PostResponse;
import com.instaclone.social.saved.service.SavedPostService;
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
