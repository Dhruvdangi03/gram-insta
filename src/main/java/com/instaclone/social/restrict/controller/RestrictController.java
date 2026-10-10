package com.instaclone.social.restrict.controller;

import com.instaclone.common.pagination.PageParams;
import com.instaclone.common.util.SecurityUtils;
import com.instaclone.social.restrict.service.RestrictService;
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
public class RestrictController {

    private final RestrictService restrictService;

    public RestrictController(RestrictService restrictService) {
        this.restrictService = restrictService;
    }

    @PostMapping("/{username}/restrict")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restrict(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        restrictService.restrict(SecurityUtils.currentUserId(jwt), username);
    }

    @DeleteMapping("/{username}/restrict")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unrestrict(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        restrictService.unrestrict(SecurityUtils.currentUserId(jwt), username);
    }

    @GetMapping("/me/restricted")
    public List<UserSummary> listRestricted(
            @RequestParam(required = false) Integer limit, @AuthenticationPrincipal Jwt jwt) {
        return restrictService.listRestricted(SecurityUtils.currentUserId(jwt), PageParams.clamp(limit));
    }
}
