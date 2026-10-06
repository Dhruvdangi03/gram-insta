package com.instaclone.user.dto;

import com.instaclone.user.enums.ViewerRelationship;

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
