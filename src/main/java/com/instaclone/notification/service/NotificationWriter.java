package com.instaclone.notification.service;

import com.instaclone.common.exception.NotFoundException;
import com.instaclone.notification.entity.Notification;
import com.instaclone.notification.enums.NotificationType;
import com.instaclone.notification.repository.NotificationRepository;
import com.instaclone.social.block.repository.UserBlockRepository;
import com.instaclone.social.follow.enums.FollowStatus;
import com.instaclone.social.follow.repository.FollowRepository;
import com.instaclone.user.entity.User;
import com.instaclone.user.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Split out of NotificationConsumer so @Transactional actually applies — a same-class method call
 * (as this used to be, invoked directly from NotificationConsumer.onMessage) bypasses Spring's
 * AOP proxy entirely, silently making the annotation a no-op.
 */
@Service
public class NotificationWriter {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final UserBlockRepository blockRepository;

    NotificationWriter(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            FollowRepository followRepository,
            UserBlockRepository blockRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
    }

    /** Empty when the event is no longer worth a notification (see isStillAWorthwhileFollowRequest). */
    @Transactional
    public Optional<Notification> createNotification(Map<String, String> body) {
        Long actorId = Long.valueOf(body.get("actorId"));
        Long recipientId = Long.valueOf(body.get("recipientId"));
        NotificationType type = NotificationType.valueOf(body.get("type"));
        // Written asynchronously, so a block placed after the triggering action but before this runs
        // must still suppress it.
        if (blockRepository.existsEitherWay(actorId, recipientId)) {
            return Optional.empty();
        }
        if (type == NotificationType.FOLLOW_REQUEST && !isStillAWorthwhileFollowRequest(actorId, recipientId)) {
            return Optional.empty();
        }

        User actor = userRepository.findById(actorId).orElseThrow(() -> new NotFoundException("User not found"));

        Notification notification = new Notification();
        notification.setRecipient(userRepository.getReferenceById(recipientId));
        notification.setActor(actor);
        notification.setType(type);
        notification.setTargetType(body.get("targetType"));
        notification.setTargetId(Long.valueOf(body.get("targetId")));
        notification.setCreatedAt(Instant.now());
        return Optional.of(notificationRepository.save(notification));
    }

    // This row is written asynchronously, after the request that triggered it has already returned —
    // by then the requester may have cancelled (or the owner declined/blocked), and
    // FollowService's cleanup, which ran synchronously, found nothing to delete yet. The same check
    // drops a duplicate left by a cancel followed by a fresh request, or by a redelivered event:
    // there is never more than one live request notification per requester.
    private boolean isStillAWorthwhileFollowRequest(Long actorId, Long recipientId) {
        return followRepository.existsByFollowerIdAndFolloweeIdAndStatus(actorId, recipientId, FollowStatus.PENDING)
                && !notificationRepository.existsByRecipientIdAndActorIdAndType(
                        recipientId, actorId, NotificationType.FOLLOW_REQUEST);
    }
}
