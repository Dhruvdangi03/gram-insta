package com.instaclone.story.dto;

import com.instaclone.user.dto.UserSummary;
import java.time.Instant;

public record StoryResponse(
        Long id, UserSummary author, String mediaUrl, Instant expiresAt, Instant createdAt, boolean seenByViewer) {}
