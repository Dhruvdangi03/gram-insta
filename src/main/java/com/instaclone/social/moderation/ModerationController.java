package com.instaclone.social.moderation;

import com.instaclone.common.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/{username}")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @PostMapping("/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        moderationService.block(SecurityUtils.currentUserId(jwt), username);
    }

    @DeleteMapping("/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        moderationService.unblock(SecurityUtils.currentUserId(jwt), username);
    }

    @PostMapping("/restrict")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restrict(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        moderationService.restrict(SecurityUtils.currentUserId(jwt), username);
    }

    @DeleteMapping("/restrict")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unrestrict(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        moderationService.unrestrict(SecurityUtils.currentUserId(jwt), username);
    }
}
