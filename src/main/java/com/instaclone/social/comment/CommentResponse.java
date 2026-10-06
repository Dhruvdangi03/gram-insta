package com.instaclone.social.comment;

import com.instaclone.user.UserSummary;
import java.time.Instant;

public record CommentResponse(
        Long id,
        UserSummary author,
        String text,
        Long parentCommentId,
        long likeCount,
        boolean likedByViewer,
        Instant createdAt) {}
