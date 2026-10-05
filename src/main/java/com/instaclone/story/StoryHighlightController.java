package com.instaclone.story;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StoryHighlightController {

    private final StoryHighlightService highlightService;

    public StoryHighlightController(StoryHighlightService highlightService) {
        this.highlightService = highlightService;
    }

    @PostMapping("/users/me/highlights")
    @ResponseStatus(HttpStatus.CREATED)
    public StoryHighlightResponse createHighlight(
            @Valid @RequestBody CreateHighlightRequest request, @AuthenticationPrincipal Jwt jwt) {
        return highlightService.createHighlight(SecurityUtils.currentUserId(jwt), request);
    }

    @PostMapping("/highlights/{id}/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addItem(
            @PathVariable Long id, @Valid @RequestBody AddHighlightItemRequest request, @AuthenticationPrincipal Jwt jwt) {
        highlightService.addItem(SecurityUtils.currentUserId(jwt), id, request);
    }

    @GetMapping("/users/{username}/highlights")
    public List<StoryHighlightResponse> getHighlights(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        return highlightService.getHighlights(username, SecurityUtils.currentUserId(jwt));
    }

    @GetMapping("/highlights/{id}")
    public StoryHighlightDetailResponse getHighlightDetail(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return highlightService.getHighlightDetail(id, SecurityUtils.currentUserId(jwt));
    }

    @DeleteMapping("/highlights/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHighlight(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        highlightService.deleteHighlight(id, SecurityUtils.currentUserId(jwt));
    }
}
