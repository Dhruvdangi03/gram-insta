package com.instaclone.story.dto;

import java.time.Instant;

public record StoryHighlightItemResponse(Long id, String mediaUrl, Instant createdAt) {}
