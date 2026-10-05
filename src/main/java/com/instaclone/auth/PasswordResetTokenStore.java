package com.instaclone.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Mirrors RefreshTokenStore exactly (opaque random token, only its SHA-256 hash persisted, Redis
 * TTL doubling as expiry, atomic GETDEL for single-use consumption) rather than introducing a
 * separate Postgres table for what's functionally the same kind of ephemeral, single-use auth
 * token — this codebase already has one idiom for that, so reuse it instead of a second one.
 */
@Component
public class PasswordResetTokenStore {

    private static final String KEY_PREFIX = "password-reset:";
    private static final Duration TTL = Duration.ofHours(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    public PasswordResetTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String issue(Long userId) {
        String rawToken = generateToken();
        redisTemplate.opsForValue().set(KEY_PREFIX + hash(rawToken), String.valueOf(userId), TTL);
        return rawToken;
    }

    public Optional<Long> consume(String rawToken) {
        String key = KEY_PREFIX + hash(rawToken);
        String userId = redisTemplate.opsForValue().getAndDelete(key);
        return Optional.ofNullable(userId).map(Long::valueOf);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
