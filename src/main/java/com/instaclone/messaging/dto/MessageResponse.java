package com.instaclone.messaging.dto;

import com.instaclone.user.dto.UserSummary;
import java.time.Instant;

public record MessageResponse(
        Long id, Long conversationId, UserSummary sender, String content, String mediaUrl, Instant createdAt) {}
