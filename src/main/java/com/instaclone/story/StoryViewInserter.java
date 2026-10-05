package com.instaclone.story;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the view-record insert in its own transaction (REQUIRES_NEW) so a losing
 * unique(story_id, viewer_id) race only aborts this isolated transaction, not the caller's —
 * mirrors SavedPostInserter/HashtagCreator's reasoning exactly.
 */
@Component
class StoryViewInserter {

    private final StoryViewRepository storyViewRepository;

    StoryViewInserter(StoryViewRepository storyViewRepository) {
        this.storyViewRepository = storyViewRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insert(StoryView view) {
        storyViewRepository.saveAndFlush(view);
    }
}
