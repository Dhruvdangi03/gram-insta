package com.instaclone.messaging.dto;

import com.instaclone.user.dto.UserSummary;
import java.time.Instant;
import java.util.List;

public record ConversationResponse(Long id, boolean group, List<UserSummary> participants, Instant createdAt) {}
