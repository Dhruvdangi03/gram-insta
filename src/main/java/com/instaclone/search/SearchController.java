package com.instaclone.search;

import com.instaclone.common.PageParams;
import com.instaclone.common.SecurityUtils;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/users")
    public List<UserSearchResult> searchUsers(
            @RequestParam String q, @RequestParam(required = false) Integer limit, @AuthenticationPrincipal Jwt jwt) {
        return searchService.searchUsers(q, PageParams.clamp(limit), SecurityUtils.currentUserId(jwt));
    }

    @GetMapping("/posts")
    public List<PostSearchResult> searchPosts(
            @RequestParam String q, @RequestParam(required = false) Integer limit, @AuthenticationPrincipal Jwt jwt) {
        return searchService.searchPosts(q, PageParams.clamp(limit), SecurityUtils.currentUserId(jwt));
    }
}
