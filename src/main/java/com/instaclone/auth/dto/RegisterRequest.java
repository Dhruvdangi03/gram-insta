package com.instaclone.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "^[a-zA-Z0-9._]+$") String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 100) String fullName) {}
