package com.instaclone.social.moderation;

import com.instaclone.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** actor did `type` to target — e.g. actor blocked/restricted target. */
@Entity
@Table(
        name = "user_moderation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"actor_id", "target_id", "type"}))
@Getter
@Setter
@NoArgsConstructor
public class UserModeration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private User target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ModerationType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
