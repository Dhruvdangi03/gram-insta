package com.instaclone.story.entity;

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
@Table(name = "story_highlight_items")
@Getter
@Setter
@NoArgsConstructor
public class StoryHighlightItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "highlight_id", nullable = false)
    private StoryHighlight highlight;

    @Column(name = "media_url", nullable = false)
    private String mediaUrl;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
