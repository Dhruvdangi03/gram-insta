package com.instaclone.media.event;

import com.instaclone.media.config.MediaStreamConfig;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays a MediaUploadedEvent onto the Redis Stream only after the DB transaction that created
 * the event commits. Publishing inside the transaction would let a consumer race ahead of the
 * commit (or fire for a transaction that later rolls back) — the same category of bug as the
 * refresh-token race fixed in the Phase 1 review, at the message-bus layer instead of Redis GETDEL.
 */
@Component
public class MediaStreamPublisher {

    private final StringRedisTemplate redisTemplate;

    public MediaStreamPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMediaUploaded(MediaUploadedEvent event) {
        Map<String, String> body = Map.of(
                "mediaId", String.valueOf(event.mediaId()),
                "postId", String.valueOf(event.postId()),
                "sourceObjectKey", event.sourceObjectKey(),
                "userId", String.valueOf(event.userId()));
        redisTemplate.opsForStream().add(MediaStreamConfig.STREAM_KEY, body);
    }
}
