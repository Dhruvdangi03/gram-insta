package com.instaclone.story;

import java.time.Instant;
import java.util.List;

public record StoryHighlightDetailResponse(
        Long id, String title, String coverUrl, Instant createdAt, List<StoryHighlightItemResponse> items) {}
