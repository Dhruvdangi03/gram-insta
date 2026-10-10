package com.instaclone.social.comment.dto;

import com.instaclone.user.dto.UserSummary;
import java.time.Instant;

public record CommentResponse(
        Long id,
        UserSummary author,
        String text,
        Long parentCommentId,
        long likeCount,
        boolean likedByViewer,
        Instant createdAt,
        /** True only for the post owner viewing a restricted user's not-yet-approved comment. */
        boolean pendingApproval) {}
