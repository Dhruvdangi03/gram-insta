package com.instaclone.common;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Opaque keyset-pagination cursor encoding the last row's (createdAt, id) seen by the client.
 * Using both fields (not just createdAt) makes the ordering total even when timestamps tie.
 */
public record Cursor(Instant createdAt, long id) {

    public String encode() {
        // Full (epochSecond, nano) precision — createdAt.toEpochMilli() would floor away the
        // sub-millisecond component that Postgres timestamptz and Instant.now() both carry,
        // which corrupts the row-value comparison this cursor drives (skips rows that share a
        // millisecond with the boundary row).
        String raw = createdAt.getEpochSecond() + ":" + createdAt.getNano() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String encoded) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 3);
            Instant createdAt = Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            return new Cursor(createdAt, Long.parseLong(parts[2]));
        } catch (Exception e) {
            throw new BadRequestException("Invalid cursor");
        }
    }
}
