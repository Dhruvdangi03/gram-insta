package com.instaclone.hashtag.dto;

/** A tag (lowercase, no '#') and how many posts the caller can discover under it. */
public record HashtagSummary(String tag, long postCount) {}
