package com.instaclone.auth;

public record AuthResult(AuthTokensResponse tokens, String refreshToken) {}
