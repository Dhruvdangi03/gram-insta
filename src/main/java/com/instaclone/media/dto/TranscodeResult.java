package com.instaclone.media.dto;

public record TranscodeResult(String videoUrl, String thumbnailUrl, Integer width, Integer height, Integer durationSec) {}
