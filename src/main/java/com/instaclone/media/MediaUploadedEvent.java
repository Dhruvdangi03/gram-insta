package com.instaclone.media;

/** Published after a reel's Post+Media rows commit; MediaStreamPublisher relays it to Redis. */
public record MediaUploadedEvent(Long mediaId, Long postId, String sourceObjectKey, Long userId) {}
