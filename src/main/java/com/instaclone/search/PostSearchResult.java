package com.instaclone.search;

import java.util.List;

public record PostSearchResult(
        Long id, String caption, Long authorId, String authorUsername, List<String> hashtags, String createdAt) {}
