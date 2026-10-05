package com.instaclone.media;

public record TranscodeResult(String videoUrl, String thumbnailUrl, Integer width, Integer height, Integer durationSec) {}
