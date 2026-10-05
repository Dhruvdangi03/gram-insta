package com.instaclone.story;

import com.instaclone.common.CursorPage;
import com.instaclone.common.PageParams;
import com.instaclone.common.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StoryController {

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @PostMapping("/stories")
    @ResponseStatus(HttpStatus.CREATED)
    public StoryResponse createStory(@Valid @RequestBody CreateStoryRequest request, @AuthenticationPrincipal Jwt jwt) {
        return storyService.createStory(SecurityUtils.currentUserId(jwt), request);
    }

    @GetMapping("/users/{username}/stories")
    public List<StoryResponse> getUserStories(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        return storyService.getUserStories(username, SecurityUtils.currentUserId(jwt));
    }

    @GetMapping("/stories/feed")
    public CursorPage<StoryResponse> getStoriesFeed(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        return storyService.getStoriesFeed(SecurityUtils.currentUserId(jwt), cursor, PageParams.clamp(limit));
    }

    @DeleteMapping("/stories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStory(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        storyService.deleteStory(id, SecurityUtils.currentUserId(jwt));
    }

    @PostMapping("/stories/{id}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markViewed(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        storyService.markViewed(id, SecurityUtils.currentUserId(jwt));
    }
}
