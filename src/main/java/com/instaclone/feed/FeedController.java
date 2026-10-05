package com.instaclone.feed;

import com.instaclone.common.CursorPage;
import com.instaclone.common.PageParams;
import com.instaclone.common.SecurityUtils;
import com.instaclone.post.PostResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping("/feed")
    public CursorPage<PostResponse> getHomeFeed(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        return feedService.getHomeFeed(SecurityUtils.currentUserId(jwt), cursor, PageParams.clamp(limit));
    }

    @GetMapping("/explore")
    public CursorPage<PostResponse> getExploreFeed(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        return feedService.getExploreFeed(SecurityUtils.currentUserId(jwt), cursor, PageParams.clamp(limit));
    }
}
