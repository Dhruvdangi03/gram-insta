package com.instaclone.notification.service;

import com.instaclone.common.exception.NotFoundException;
import com.instaclone.common.pagination.Cursor;
import com.instaclone.common.pagination.CursorPage;
import com.instaclone.notification.dto.NotificationResponse;
import com.instaclone.notification.entity.Notification;
import com.instaclone.notification.repository.NotificationRepository;
import com.instaclone.user.dto.UserSummary;
import com.instaclone.user.entity.User;
import com.instaclone.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CursorPage<NotificationResponse> getNotifications(Long recipientId, String cursor, int limit) {
        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<Notification> rows = decoded == null
                ? notificationRepository.findFirstPageByRecipientId(recipientId, limit + 1)
                : notificationRepository.findPageByRecipientIdAfterCursor(
                        recipientId, decoded.createdAt(), decoded.id(), limit + 1);

        CursorPage<Notification> page =
                CursorPage.of(rows, limit, n -> new Cursor(n.getCreatedAt(), n.getId()).encode());

        Set<Long> actorIds = page.items().stream().map(n -> n.getActor().getId()).collect(Collectors.toSet());
        Map<Long, UserSummary> actorsById = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, UserSummary::from));

        List<NotificationResponse> items = page.items().stream()
                .map(n -> NotificationResponse.from(n, actorsById.get(n.getActor().getId())))
                .toList();
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }

    @Transactional
    public void markRead(Long notificationId, Long recipientId) {
        int updated = notificationRepository.markRead(notificationId, recipientId);
        if (updated == 0) {
            throw new NotFoundException("Notification not found");
        }
    }
}
