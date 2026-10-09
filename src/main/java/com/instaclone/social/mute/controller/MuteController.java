package com.instaclone.social.mute.controller;

import com.instaclone.common.pagination.PageParams;
import com.instaclone.common.util.SecurityUtils;
import com.instaclone.social.mute.service.MuteService;
import com.instaclone.user.dto.UserSummary;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class MuteController {

    private final MuteService muteService;

    public MuteController(MuteService muteService) {
        this.muteService = muteService;
    }

    @PostMapping("/{username}/mute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mute(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        muteService.mute(SecurityUtils.currentUserId(jwt), username);
    }

    @DeleteMapping("/{username}/mute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unmute(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        muteService.unmute(SecurityUtils.currentUserId(jwt), username);
    }

    @GetMapping("/me/muted")
    public List<UserSummary> listMuted(@RequestParam(required = false) Integer limit, @AuthenticationPrincipal Jwt jwt) {
        return muteService.listMuted(SecurityUtils.currentUserId(jwt), PageParams.clamp(limit));
    }
}
