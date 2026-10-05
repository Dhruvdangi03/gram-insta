package com.instaclone.reel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReelRequest(
        @Size(max = 2200) String caption, @Size(max = 255) String location, @NotNull @Valid MediaItem media) {

    // No width/height/duration here, unlike CreatePostRequest — for a reel, ffprobe on the actual
    // uploaded file is the authoritative source for those once the transcode worker runs, not the client.
    public record MediaItem(@NotNull String url) {}
}
