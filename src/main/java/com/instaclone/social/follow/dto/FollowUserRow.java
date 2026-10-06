package com.instaclone.social.follow.dto;

import java.time.Instant;

/** A follower/following list row: the relationship's own cursor fields plus the other user's display fields. */
public interface FollowUserRow {
    Long getFollowId();

    Instant getFollowCreatedAt();

    Long getUserId();

    String getUsername();

    String getFullName();

    String getProfilePictureUrl();

    boolean getIsVerified();
}
