package com.instaclone.reel.controller;

import com.instaclone.common.pagination.CursorPage;
import com.instaclone.common.pagination.PageParams;
import com.instaclone.common.util.SecurityUtils;
import com.instaclone.post.dto.PostResponse;
import com.instaclone.reel.dto.CreateReelRequest;
import com.instaclone.reel.service.ReelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reels")
public class ReelController {

    private final ReelService reelService;

    public ReelController(ReelService reelService) {
        this.reelService = reelService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse createReel(@Valid @RequestBody CreateReelRequest request, @AuthenticationPrincipal Jwt jwt) {
        return reelService.createReel(SecurityUtils.currentUserId(jwt), request);
    }

    @GetMapping("/feed")
    public CursorPage<PostResponse> getReelsFeed(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        return reelService.getReelsFeed(SecurityUtils.currentUserId(jwt), cursor, PageParams.clamp(limit));
    }
}
