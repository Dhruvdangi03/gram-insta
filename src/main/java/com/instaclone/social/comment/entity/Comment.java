package com.instaclone.social.comment.entity;

import com.instaclone.post.entity.Post;
import com.instaclone.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne()
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne()
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne()
    @JoinColumn(name = "parent_comment_id")
    private Comment parent;

    @Column(length = 2200, nullable = false)
    private String text;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isReply() {
        return parent != null;
    }
}
