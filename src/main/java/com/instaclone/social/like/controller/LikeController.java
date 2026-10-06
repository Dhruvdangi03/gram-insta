package com.instaclone.social.like.controller;

import com.instaclone.common.util.SecurityUtils;
import com.instaclone.social.like.dto.LikeCountResponse;
import com.instaclone.social.like.service.LikeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// No class-level @RequestMapping: likes live under two different resource paths
// (posts and comments), so each method carries its own full path instead.
@RestController
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping("/posts/{postId}/likes")
    public LikeCountResponse like(@PathVariable Long postId, @AuthenticationPrincipal Jwt jwt) {
        return likeService.likePost(SecurityUtils.currentUserId(jwt), postId);
    }

    @DeleteMapping("/posts/{postId}/likes")
    public LikeCountResponse unlike(@PathVariable Long postId, @AuthenticationPrincipal Jwt jwt) {
        return likeService.unlikePost(SecurityUtils.currentUserId(jwt), postId);
    }

    @PostMapping("/comments/{commentId}/likes")
    public LikeCountResponse likeComment(@PathVariable Long commentId, @AuthenticationPrincipal Jwt jwt) {
        return likeService.likeComment(SecurityUtils.currentUserId(jwt), commentId);
    }

    @DeleteMapping("/comments/{commentId}/likes")
    public LikeCountResponse unlikeComment(@PathVariable Long commentId, @AuthenticationPrincipal Jwt jwt) {
        return likeService.unlikeComment(SecurityUtils.currentUserId(jwt), commentId);
    }
}
