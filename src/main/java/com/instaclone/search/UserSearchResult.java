package com.instaclone.search;

public record UserSearchResult(Long id, String username, String fullName, String profilePictureUrl, boolean isVerified) {}
