package com.instaclone.media;

import com.instaclone.post.MediaRepository;
import com.instaclone.post.MediaStatus;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

/**
 * Picks up MediaUploadedEvent messages off the Redis Stream and runs the actual transcode.
 * ACKs unconditionally (success or failure) — Phase 2 has no retry/DLQ, so a message that isn't
 * ACKed would sit in the consumer group's pending-entries list forever (see the build plan's
 * "Reliability scope" note). Never holds a DB transaction open across the FFmpeg subprocess call:
 * each status update is its own short, separate save.
 */
@Component
public class MediaUploadConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(MediaUploadConsumer.class);

    private final MediaRepository mediaRepository;
    private final MediaProcessingService mediaProcessingService;
    private final StringRedisTemplate redisTemplate;

    public MediaUploadConsumer(
            MediaRepository mediaRepository,
            MediaProcessingService mediaProcessingService,
            StringRedisTemplate redisTemplate) {
        this.mediaRepository = mediaRepository;
        this.mediaProcessingService = mediaProcessingService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> body = message.getValue();
        Long mediaId = Long.valueOf(body.get("mediaId"));
        String sourceObjectKey = body.get("sourceObjectKey");
        Long userId = Long.valueOf(body.get("userId"));
        log.info("Processing transcode job for media {} from object key {}", mediaId, sourceObjectKey);
        try {
            updateStatus(mediaId, MediaStatus.PROCESSING);
            log.info("Updated media {} status to PROCESSING, starting transcode", mediaId);
            TranscodeResult result = mediaProcessingService.transcodeAndThumbnail(sourceObjectKey, userId);
            log.info("Transcode succeeded for media {}, applying result", mediaId);
            applyResult(mediaId, result);
            log.info("Reel transcode complete for media {}: {}x{} {}s", mediaId, result.width(), result.height(), result.durationSec());
        } catch (Exception e) {
            log.error("Reel transcode failed for media {} from object key {}", mediaId, sourceObjectKey, e);
            updateStatus(mediaId, MediaStatus.FAILED);
        } finally {
            redisTemplate.opsForStream().acknowledge(MediaStreamConfig.STREAM_KEY, MediaStreamConfig.CONSUMER_GROUP, message.getId());
        }
    }

    private void updateStatus(Long mediaId, MediaStatus status) {
        mediaRepository.findById(mediaId).ifPresent(media -> {
            media.setStatus(status);
            mediaRepository.save(media);
        });
    }

    private void applyResult(Long mediaId, TranscodeResult result) {
        mediaRepository.findById(mediaId).ifPresent(media -> {
            media.setUrl(result.videoUrl());
            media.setThumbnailUrl(result.thumbnailUrl());
            media.setWidth(result.width());
            media.setHeight(result.height());
            media.setDurationSec(result.durationSec());
            media.setStatus(MediaStatus.READY);
            mediaRepository.save(media);
        });
    }
}
