package com.instaclone.social.block.controller;

import com.instaclone.common.pagination.PageParams;
import com.instaclone.common.util.SecurityUtils;
import com.instaclone.social.block.service.BlockService;
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
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @PostMapping("/{username}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        blockService.block(SecurityUtils.currentUserId(jwt), username);
    }

    @DeleteMapping("/{username}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        blockService.unblock(SecurityUtils.currentUserId(jwt), username);
    }

    @GetMapping("/me/blocked")
    public List<UserSummary> listBlocked(@RequestParam(required = false) Integer limit, @AuthenticationPrincipal Jwt jwt) {
        return blockService.listBlocked(SecurityUtils.currentUserId(jwt), PageParams.clamp(limit));
    }
}
