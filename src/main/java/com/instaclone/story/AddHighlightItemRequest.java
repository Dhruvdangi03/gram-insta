package com.instaclone.story;

import jakarta.validation.constraints.NotNull;

public record AddHighlightItemRequest(@NotNull Long storyId) {}
