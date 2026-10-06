package com.instaclone.post.dto;

public record PresignedUploadResponse(String uploadUrl, String objectKey, String publicUrl) {}
