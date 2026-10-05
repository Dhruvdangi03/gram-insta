package com.instaclone.story;

import java.time.Instant;

public record StoryHighlightItemResponse(Long id, String mediaUrl, Instant createdAt) {}
