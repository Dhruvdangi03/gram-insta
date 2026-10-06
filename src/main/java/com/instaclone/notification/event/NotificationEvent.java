package com.instaclone.notification.event;

import com.instaclone.notification.enums.NotificationType;

/** Published after the triggering like/comment/follow commits; NotificationStreamPublisher relays it to Redis. */
public record NotificationEvent(
        Long recipientId, Long actorId, NotificationType type, String targetType, Long targetId) {}
