package com.instaclone.post;

import com.instaclone.user.UserSummary;
import java.time.Instant;
import java.util.List;

public record PostResponse(
        Long id,
        UserSummary author,
        String caption,
        String location,
        PostType type,
        int mediaCount,
        long likeCount,
        long commentCount,
        boolean likedByViewer,
        boolean savedByViewer,
        Instant createdAt,
        List<MediaResponse> media,
        List<String> hashtags) {}
