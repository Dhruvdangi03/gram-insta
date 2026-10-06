package com.instaclone.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 30)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    @Column(length = 150)
    private String bio;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate = false;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = false;

    @Column(name = "is_business", nullable = false)
    private boolean isBusiness = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Single source of truth for private-account visibility; use via {@link ProfileVisibilityService}. */
    public boolean isVisibleTo(User viewer, boolean viewerFollowsThisUser) {
        if (!isPrivate) {
            return true;
        }
        if (viewer == null) {
            return false;
        }
        return viewer.getId().equals(id) || viewerFollowsThisUser;
    }

    // Id-based identity, not the default reference identity — User is used as Set<User> elements
    // (e.g. Conversation.participants), where two instances loaded in different sessions but
    // representing the same row must be treated as equal. hashCode is a constant rather than
    // derived from id: id is null before the first persist, and a mutable hashCode would break
    // membership in a HashSet the entity was added to before that.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
