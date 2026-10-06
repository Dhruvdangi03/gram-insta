package com.instaclone.search.dto;

public record UserSearchResult(Long id, String username, String fullName, String profilePictureUrl, boolean isVerified) {}
