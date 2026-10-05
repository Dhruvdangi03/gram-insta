package com.instaclone.hashtag;

import com.instaclone.common.CursorPage;
import com.instaclone.common.PageParams;
import com.instaclone.common.SecurityUtils;
import com.instaclone.post.PostResponse;
import com.instaclone.post.PostService;
import java.util.Locale;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HashtagController {

    private final PostService postService;

    public HashtagController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/hashtags/{tag}/posts")
    public CursorPage<PostResponse> getPostsByHashtag(
            @PathVariable String tag,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        return postService.getPostsByHashtag(
                tag.toLowerCase(Locale.ROOT), SecurityUtils.currentUserId(jwt), cursor, PageParams.clamp(limit));
    }
}
