package com.instaclone.search;

import java.util.Map;

/**
 * Published after the triggering write (post/reel creation, profile update) commits.
 * SearchStreamPublisher relays it to Redis. fields is ignored when delete is true.
 */
public record SearchIndexEvent(String index, String documentId, Map<String, Object> fields, boolean delete) {

    public static SearchIndexEvent upsert(String index, String documentId, Map<String, Object> fields) {
        return new SearchIndexEvent(index, documentId, fields, false);
    }

    public static SearchIndexEvent delete(String index, String documentId) {
        return new SearchIndexEvent(index, documentId, Map.of(), true);
    }
}
