package com.instaclone.story.dto;

import java.time.Instant;

public record StoryHighlightResponse(Long id, String title, String coverUrl, Instant createdAt) {}
