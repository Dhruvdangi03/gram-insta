package com.instaclone.auth.dto;

public record AuthResult(AuthTokensResponse tokens, String refreshToken) {}
