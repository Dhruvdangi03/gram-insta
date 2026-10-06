package com.instaclone.common.pagination;

public class PageParams {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    private PageParams() {}

    public static int clamp(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
