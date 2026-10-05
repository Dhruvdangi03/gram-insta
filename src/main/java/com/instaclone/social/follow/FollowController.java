package com.instaclone.social.follow;

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
@RequestMapping("/users/{username}/follow")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping
    public FollowStatusResponse follow(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        return followService.follow(SecurityUtils.currentUserId(jwt), username);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        followService.unfollow(SecurityUtils.currentUserId(jwt), username);
    }

    @PostMapping("/accept")
    public FollowStatusResponse acceptFollowRequest(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        return followService.acceptFollowRequest(SecurityUtils.currentUserId(jwt), username);
    }

    @DeleteMapping("/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectFollowRequest(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        followService.rejectFollowRequest(SecurityUtils.currentUserId(jwt), username);
    }

    // Like accept/reject: {username} is the OTHER account, and the caller is the one being followed —
    // so this removes {username} as the caller's follower (DELETE on the bare path is the reverse,
    // the caller unfollowing {username}).
    @DeleteMapping("/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFollower(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        followService.removeFollower(SecurityUtils.currentUserId(jwt), username);
    }
}
