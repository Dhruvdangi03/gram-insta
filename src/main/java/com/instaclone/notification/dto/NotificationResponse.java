package com.instaclone.notification.dto;

import com.instaclone.notification.entity.Notification;
import com.instaclone.notification.enums.NotificationType;
import com.instaclone.user.dto.UserSummary;
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
