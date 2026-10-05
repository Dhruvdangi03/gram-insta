package com.instaclone.auth;

public record AuthTokensResponse(String accessToken, String tokenType, long expiresInSeconds, UserSummaryResponse user) {}
