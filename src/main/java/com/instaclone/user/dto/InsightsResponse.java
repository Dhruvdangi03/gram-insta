package com.instaclone.user.dto;

/** Aggregate counts only — plain COUNT/SUM over existing tables, no new analytics infrastructure
 * or time-series data (there's no historical tracking to chart growth over time). */
public record InsightsResponse(
        long postCount, long followerCount, long followingCount, long totalLikes, long totalComments) {}
