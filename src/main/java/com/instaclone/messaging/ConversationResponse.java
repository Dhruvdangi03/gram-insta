package com.instaclone.messaging;

import com.instaclone.user.UserSummary;
import java.time.Instant;
import java.util.List;

public record ConversationResponse(Long id, boolean group, List<UserSummary> participants, Instant createdAt) {}
