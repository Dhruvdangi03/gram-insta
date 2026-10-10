package com.instaclone.social.restrict.entity;

import com.instaclone.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_restrictions")
@Getter
@Setter
@NoArgsConstructor
public class UserRestriction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne()
    @JoinColumn(name = "restrictor_id", nullable = false)
    private User restrictor;

    @ManyToOne()
    @JoinColumn(name = "restricted_id", nullable = false)
    private User restricted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
