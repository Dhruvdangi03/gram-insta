package com.instaclone.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateUploadUrlRequest(
        @NotBlank
                @Pattern(
                        regexp = "^image/(jpeg|png|webp)$|^video/(mp4|quicktime|webm)$",
                        message = "Only JPEG/PNG/WEBP images or MP4/MOV/WEBM videos are accepted")
                String contentType) {}
