package com.instaclone.story;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryHighlightRepository extends JpaRepository<StoryHighlight, Long> {

    List<StoryHighlight> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Row-locks the highlight for the rest of the caller's transaction, serializing concurrent
    // addItem calls against the same highlight so their countByHighlightId-then-insert position
    // assignment can't race (see StoryHighlightService.addItem).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from StoryHighlight h where h.id = :id")
    Optional<StoryHighlight> findByIdForUpdate(@Param("id") Long id);
}
