package com.instaclone.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.stories")
public record StoryProperties(Duration defaultTtl, Duration maxTtl) {}
