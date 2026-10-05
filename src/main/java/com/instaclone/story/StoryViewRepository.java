package com.instaclone.story;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryViewRepository extends JpaRepository<StoryView, Long> {

    boolean existsByStoryIdAndViewerId(Long storyId, Long viewerId);

    @Query("select v.story.id from StoryView v where v.viewer.id = :viewerId and v.story.id in :storyIds")
    List<Long> findViewedStoryIds(@Param("viewerId") Long viewerId, @Param("storyIds") List<Long> storyIds);
}
