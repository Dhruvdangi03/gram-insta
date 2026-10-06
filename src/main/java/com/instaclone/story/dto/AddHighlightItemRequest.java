package com.instaclone.story.dto;

import jakarta.validation.constraints.NotNull;

public record AddHighlightItemRequest(@NotNull Long storyId) {}
