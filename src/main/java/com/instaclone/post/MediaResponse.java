package com.instaclone.post;

public record MediaResponse(
        Long id,
        String url,
        MediaType mediaType,
        Integer width,
        Integer height,
        Integer durationSec,
        int position,
        String thumbnailUrl,
        MediaStatus status) {
    public static MediaResponse from(Media media) {
        return new MediaResponse(
                media.getId(),
                media.getUrl(),
                media.getMediaType(),
                media.getWidth(),
                media.getHeight(),
                media.getDurationSec(),
                media.getPosition(),
                media.getThumbnailUrl(),
                media.getStatus());
    }
}
