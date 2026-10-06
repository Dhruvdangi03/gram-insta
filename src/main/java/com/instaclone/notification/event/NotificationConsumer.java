package com.instaclone.notification.event;

import com.instaclone.notification.config.NotificationStreamConfig;
import com.instaclone.notification.dto.NotificationResponse;
import com.instaclone.notification.entity.Notification;
import com.instaclone.notification.service.NotificationWriter;
import com.instaclone.user.dto.UserSummary;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Picks up NotificationEvent messages off the Redis Stream, creates the durable Notification row,
 * and pushes it live to a connected recipient over WebSocket. ACKs unconditionally — same
 * reliability scope as com.instaclone.media.event.MediaUploadConsumer (no retry/DLQ in this phase).
 */
@Component
public class NotificationConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationWriter notificationWriter;
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationConsumer(
            NotificationWriter notificationWriter, StringRedisTemplate redisTemplate, SimpMessagingTemplate messagingTemplate) {
        this.notificationWriter = notificationWriter;
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> body = message.getValue();
        try {
            // Delegates to a separate bean so its @Transactional actually applies — see NotificationWriter's Javadoc.
            notificationWriter.createNotification(body).ifPresent(this::pushLive);
        } catch (Exception e) {
            log.error("Failed to process notification event {}", body, e);
        } finally {
            redisTemplate
                    .opsForStream()
                    .acknowledge(NotificationStreamConfig.STREAM_KEY, NotificationStreamConfig.CONSUMER_GROUP, message.getId());
        }
    }

    private void pushLive(Notification notification) {
        NotificationResponse response = NotificationResponse.from(notification, UserSummary.from(notification.getActor()));
        messagingTemplate.convertAndSendToUser(
                String.valueOf(notification.getRecipient().getId()), "/queue/notifications", response);
    }
}
