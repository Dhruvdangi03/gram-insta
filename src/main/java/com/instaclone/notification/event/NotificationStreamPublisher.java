package com.instaclone.notification.event;

import com.instaclone.notification.config.NotificationStreamConfig;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays a NotificationEvent onto the Redis Stream only after the triggering transaction commits
 * — same reasoning as com.instaclone.media.event.MediaStreamPublisher: a consumer must never race an
 * uncommitted or rolled-back row.
 */
@Component
public class NotificationStreamPublisher {

    private final StringRedisTemplate redisTemplate;

    public NotificationStreamPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotification(NotificationEvent event) {
        Map<String, String> body = Map.of(
                "recipientId", String.valueOf(event.recipientId()),
                "actorId", String.valueOf(event.actorId()),
                "type", event.type().name(),
                "targetType", event.targetType(),
                "targetId", String.valueOf(event.targetId()));
        redisTemplate.opsForStream().add(NotificationStreamConfig.STREAM_KEY, body);
    }
}
