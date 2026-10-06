package com.instaclone.user;

public record UserProfileResponse(
        Long id,
        String username,
        String fullName,
        String bio,
        String profilePictureUrl,
        boolean isPrivate,
        boolean isVerified,
        boolean isBusiness,
        long postCount,
        long followerCount,
        long followingCount,
        ViewerRelationship viewerRelationship) {}
