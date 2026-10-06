package com.instaclone.story.repository;

import com.instaclone.story.entity.StoryHighlightItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryHighlightItemRepository extends JpaRepository<StoryHighlightItem, Long> {

    List<StoryHighlightItem> findByHighlightIdOrderByPosition(Long highlightId);

    int countByHighlightId(Long highlightId);
}
