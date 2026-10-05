package com.instaclone.story;

import com.instaclone.user.UserSummary;
import java.time.Instant;

public record StoryResponse(
        Long id, UserSummary author, String mediaUrl, Instant expiresAt, Instant createdAt, boolean seenByViewer) {}
