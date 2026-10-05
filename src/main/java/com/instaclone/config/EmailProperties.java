package com.instaclone.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(String resendApiKey, String fromAddress, String appBaseUrl) {}
