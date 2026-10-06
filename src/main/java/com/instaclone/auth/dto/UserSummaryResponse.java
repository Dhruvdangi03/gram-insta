package com.instaclone.auth.dto;

import com.instaclone.user.entity.User;

public record UserSummaryResponse(Long id, String username, String fullName, String profilePictureUrl) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getUsername(), user.getFullName(), user.getProfilePictureUrl());
    }
}
