package com.instaclone.user.dto;

import com.instaclone.user.entity.User;

public record UserSummary(Long id, String username, String fullName, String profilePictureUrl, boolean isVerified) {
    public static UserSummary from(User user) {
        return new UserSummary(
                user.getId(), user.getUsername(), user.getFullName(), user.getProfilePictureUrl(), user.isVerified());
    }
}
