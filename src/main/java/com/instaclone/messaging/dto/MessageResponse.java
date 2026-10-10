package com.instaclone.messaging.dto;

import com.instaclone.post.dto.PostResponse;
import com.instaclone.user.dto.UserSummary;
import java.time.Instant;

public record MessageResponse(
        Long id,
        Long conversationId,
        UserSummary sender,
        String content,
        String mediaUrl,
        PostResponse sharedPost,
        Instant createdAt) {}
