package com.instaclone.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Opaque keyset-pagination cursor for feeds ordered by a mutable rank (e.g. like_count) rather
 * than creation time — see Cursor for the time-based equivalent. A post's rank can shift between
 * page fetches since likes keep arriving; that's an accepted tradeoff for a trending-style feed,
 * not a bug.
 */
public record RankCursor(long rank, long id) {

    public String encode() {
        String raw = rank + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static RankCursor decode(String encoded) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 2);
            return new RankCursor(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception e) {
            throw new BadRequestException("Invalid cursor");
        }
    }
}
