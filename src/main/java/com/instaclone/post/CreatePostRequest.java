package com.instaclone.post;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreatePostRequest(
        @Size(max = 2200) String caption,
        @Size(max = 255) String location,
        @NotEmpty @Size(max = 10) @Valid List<MediaItem> media) {

    public record MediaItem(@NotNull String url, Integer width, Integer height) {}
}
