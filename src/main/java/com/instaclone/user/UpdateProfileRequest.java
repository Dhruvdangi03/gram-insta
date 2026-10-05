package com.instaclone.user;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 100) String fullName,
        @Size(max = 150) String bio,
        String profilePictureUrl,
        Boolean isPrivate,
        Boolean isBusiness) {}
