package com.instaclone.messaging.entity;

import com.instaclone.messaging.enums.ConversationStatus;
import com.instaclone.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "is_group", nullable = false)
    private boolean group;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Null until the first message is sent; updated on every send. Drives listConversations'
    // ordering and its cursor — see MessageService.sendMessage / ConversationRepository.
    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 10)
    private ConversationStatus status = ConversationStatus.ACCEPTED;

    // Who started the conversation; only meaningful while status is PENDING (the recipient is
    // everyone else). Null for pre-V19 rows and if the initiating user is later deleted.
    @Column(name = "initiator_id")
    private Long initiatorId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "conversation_participants",
            joinColumns = @JoinColumn(name = "conversation_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> participants = new HashSet<>();
}
