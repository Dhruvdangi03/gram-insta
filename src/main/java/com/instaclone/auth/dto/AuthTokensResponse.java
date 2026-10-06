package com.instaclone.auth.dto;

public record AuthTokensResponse(String accessToken, String tokenType, long expiresInSeconds, UserSummaryResponse user) {}
