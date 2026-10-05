package com.instaclone.messaging;

import com.instaclone.user.UserSummary;
import java.time.Instant;

public record MessageResponse(
        Long id, Long conversationId, UserSummary sender, String content, String mediaUrl, Instant createdAt) {}
