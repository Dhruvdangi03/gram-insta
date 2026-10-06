package com.instaclone.common.pagination;

import java.util.List;

public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {

    /** cursorOf returns the already-encoded cursor string for a row, so any cursor type can be used. */
    public static <T> CursorPage<T> of(List<T> pageItems, int requestedLimit, java.util.function.Function<T, String> cursorOf) {
        boolean hasMore = pageItems.size() > requestedLimit;
        List<T> trimmed = hasMore ? pageItems.subList(0, requestedLimit) : pageItems;
        String next = hasMore ? cursorOf.apply(trimmed.get(trimmed.size() - 1)) : null;
        return new CursorPage<>(trimmed, next, hasMore);
    }
}
