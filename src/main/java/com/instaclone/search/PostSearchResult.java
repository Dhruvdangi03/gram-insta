package com.instaclone.search;

public record PostSearchResult(Long id, String caption, Long authorId, String authorUsername, String createdAt) {}
