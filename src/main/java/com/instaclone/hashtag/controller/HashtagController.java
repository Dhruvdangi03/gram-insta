package com.instaclone.hashtag.controller;

import com.instaclone.common.pagination.CursorPage;
import com.instaclone.common.pagination.PageParams;
import com.instaclone.common.util.SecurityUtils;
import com.instaclone.hashtag.dto.HashtagSummary;
import com.instaclone.hashtag.service.HashtagService;
import com.instaclone.post.dto.PostResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HashtagController {

    private final HashtagService hashtagService;

    public HashtagController(HashtagService hashtagService) {
        this.hashtagService = hashtagService;
    }

    @GetMapping("/hashtags/{tag}")
    public HashtagSummary getHashtag(@PathVariable String tag, @AuthenticationPrincipal Jwt jwt) {
        return hashtagService.getHashtag(tag, SecurityUtils.currentUserId(jwt));
    }

    @GetMapping("/hashtags/{tag}/posts")
    public CursorPage<PostResponse> getPosts(
            @PathVariable String tag,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        return hashtagService.getPosts(tag, SecurityUtils.currentUserId(jwt), cursor, PageParams.clamp(limit));
    }

    @GetMapping("/search/hashtags")
    public List<HashtagSummary> searchHashtags(
            @RequestParam String q, @RequestParam(required = false) Integer limit, @AuthenticationPrincipal Jwt jwt) {
        return hashtagService.search(q, PageParams.clamp(limit), SecurityUtils.currentUserId(jwt));
    }
}
