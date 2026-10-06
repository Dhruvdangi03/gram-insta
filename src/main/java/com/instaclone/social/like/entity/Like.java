package com.instaclone.social.like.entity;

import com.instaclone.social.like.enums.LikeableType;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "likes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "likeable_type", "likeable_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "likeable_type", nullable = false, length = 10)
    private LikeableType likeableType;

    @Column(name = "likeable_id", nullable = false)
    private Long likeableId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
