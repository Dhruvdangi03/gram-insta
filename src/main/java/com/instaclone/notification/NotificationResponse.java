package com.instaclone.notification;

import com.instaclone.user.UserSummary;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        UserSummary actor,
        NotificationType type,
        String targetType,
        Long targetId,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification, UserSummary actor) {
        return new NotificationResponse(
                notification.getId(),
                actor,
                notification.getType(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
