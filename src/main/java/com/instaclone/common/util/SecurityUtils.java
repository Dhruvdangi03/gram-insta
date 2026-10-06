package com.instaclone.common.util;

import org.springframework.security.oauth2.jwt.Jwt;

public class SecurityUtils {
    private SecurityUtils() {}

    public static Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
