package com.instaclone.messaging;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes a sent message over STOMP only after the transaction that persisted it commits — same
 * reasoning as com.instaclone.notification.NotificationStreamPublisher: pushing synchronously
 * inside the transaction would let a recipient see a message for a row that could still roll back.
 */
@Component
class MessagePushPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    MessagePushPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onMessageSent(MessageSentEvent event) {
        for (Long recipientId : event.recipientIds()) {
            messagingTemplate.convertAndSendToUser(String.valueOf(recipientId), "/queue/messages", event.response());
        }
    }
}
