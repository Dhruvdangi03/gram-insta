package com.instaclone.social.like;

import com.instaclone.common.SecurityUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/posts/{postId}/likes")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping
    public LikeCountResponse like(@PathVariable Long postId, @AuthenticationPrincipal Jwt jwt) {
        return likeService.likePost(SecurityUtils.currentUserId(jwt), postId);
    }

    @DeleteMapping
    public LikeCountResponse unlike(@PathVariable Long postId, @AuthenticationPrincipal Jwt jwt) {
        return likeService.unlikePost(SecurityUtils.currentUserId(jwt), postId);
    }
}
