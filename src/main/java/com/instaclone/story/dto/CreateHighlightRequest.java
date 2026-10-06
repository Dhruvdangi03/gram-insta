package com.instaclone.story.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateHighlightRequest(@NotBlank @Size(max = 50) String title, String coverUrl) {}
