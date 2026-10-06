package com.instaclone.social.comment.controller;

import com.instaclone.common.pagination.CursorPage;
import com.instaclone.common.pagination.PageParams;
import com.instaclone.common.util.SecurityUtils;
import com.instaclone.social.comment.dto.CommentResponse;
import com.instaclone.social.comment.dto.CreateCommentRequest;
import com.instaclone.social.comment.service.CommentService;
import jakarta.validation.Valid;
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
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(
            @PathVariable Long postId, @Valid @RequestBody CreateCommentRequest request, @AuthenticationPrincipal Jwt jwt) {
        return commentService.addComment(postId, SecurityUtils.currentUserId(jwt), request);
    }

    @GetMapping("/posts/{postId}/comments")
    public CursorPage<CommentResponse> getComments(
            @PathVariable Long postId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        return commentService.getComments(postId, SecurityUtils.currentUserId(jwt), cursor, PageParams.clamp(limit));
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        commentService.deleteComment(id, SecurityUtils.currentUserId(jwt));
    }
}
