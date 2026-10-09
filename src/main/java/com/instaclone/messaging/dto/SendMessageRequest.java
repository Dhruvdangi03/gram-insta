package com.instaclone.messaging.dto;

import jakarta.validation.constraints.Size;

/** At least one of content/mediaUrl/sharedPostId must be present — checked in MessageService, not
 * here, since Bean Validation has no simple built-in for "at least one of these fields". */
public record SendMessageRequest(
        @Size(max = 1000) String content, @Size(max = 2048) String mediaUrl, Long sharedPostId) {}
