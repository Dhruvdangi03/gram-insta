package com.instaclone.social.saved;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the save-post insert attempt in its own transaction (REQUIRES_NEW) so a losing
 * unique(user_id, post_id) race only aborts this isolated transaction, not the caller's: on
 * Postgres a failed statement poisons the entire enclosing
 * transaction, so catching the constraint violation has to happen outside the transaction that hit
 * it. Has to be its own bean, not a private method on SavedPostService, since Spring can't apply
 * REQUIRES_NEW to a self-invoked call.
 */
@Component
class SavedPostInserter {

    private final SavedPostRepository savedPostRepository;

    SavedPostInserter(SavedPostRepository savedPostRepository) {
        this.savedPostRepository = savedPostRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insert(SavedPost saved) {
        savedPostRepository.saveAndFlush(saved);
    }
}
