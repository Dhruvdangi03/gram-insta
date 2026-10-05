package com.instaclone.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(Duration accessTokenTtl, Duration refreshTokenTtl, boolean cookieSecure) {}
