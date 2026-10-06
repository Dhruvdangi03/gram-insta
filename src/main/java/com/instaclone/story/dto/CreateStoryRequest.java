package com.instaclone.story.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** expiresInSeconds is optional — defaults to app.stories.default-ttl, capped at app.stories.max-ttl.
 * Letting the caller shorten it is what makes a fast test expiry (e.g. 2 minutes) possible at all. */
public record CreateStoryRequest(@NotBlank String mediaUrl, @Positive Long expiresInSeconds) {}
