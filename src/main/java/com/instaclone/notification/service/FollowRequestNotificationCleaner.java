package com.instaclone.notification.service;

import com.instaclone.notification.enums.NotificationType;
import com.instaclone.notification.repository.NotificationRepository;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Removes "requested to follow you" notifications once the request they describe is resolved
 * (cancelled, declined, accepted, blocked, or the account went public) and tells the recipient's
 * open session afterwards, so its notification list, unread dot, and Follow Requests count drop the
 * row live instead of showing a notification that links to a requests list it's no longer in.
 * Every call must run inside the transaction that resolves the request.
 */
@Component
public class FollowRequestNotificationCleaner {

    record NotificationsChangedEvent(Long recipientId) {}

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SimpMessagingTemplate messagingTemplate;

    public FollowRequestNotificationCleaner(
            NotificationRepository notificationRepository,
            ApplicationEventPublisher eventPublisher,
            SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.eventPublisher = eventPublisher;
        this.messagingTemplate = messagingTemplate;
    }

    /** One requester's notification on the recipient's account. */
    public void clear(Long recipientId, Long actorId) {
        notificationRepository.deleteByRecipientIdAndActorIdAndType(recipientId, actorId, NotificationType.FOLLOW_REQUEST);
        eventPublisher.publishEvent(new NotificationsChangedEvent(recipientId));
    }

    /** Every pending-request notification on the recipient's account (it just went public). */
    public void clearAll(Long recipientId) {
        notificationRepository.deleteByRecipientIdAndType(recipientId, NotificationType.FOLLOW_REQUEST);
        eventPublisher.publishEvent(new NotificationsChangedEvent(recipientId));
    }

    // After commit, like NotificationStreamPublisher — the client's refetch must never race the delete.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onNotificationsChanged(NotificationsChangedEvent event) {
        messagingTemplate.convertAndSendToUser(
                String.valueOf(event.recipientId()), "/queue/notifications-changed", Map.of());
    }
}
