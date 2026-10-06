package com.instaclone.search.dto;

public record PostSearchResult(Long id, String caption, Long authorId, String authorUsername, String createdAt) {}
