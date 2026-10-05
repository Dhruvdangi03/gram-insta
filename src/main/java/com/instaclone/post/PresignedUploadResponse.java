package com.instaclone.post;

public record PresignedUploadResponse(String uploadUrl, String objectKey, String publicUrl) {}
