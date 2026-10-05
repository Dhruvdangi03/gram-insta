package com.instaclone.hashtag;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the hashtag insert attempt in its own transaction (REQUIRES_NEW) so a losing unique-
 * constraint race only aborts this isolated transaction, not the caller's — letting HashtagService
 * retry the lookup afterward on its own, still-valid transaction. On Postgres, a failed statement
 * poisons the entire enclosing transaction (any later statement on it fails with "current
 * transaction is aborted"), so retrying the lookup in-place, in the same transaction as the failed
 * insert, cannot work. Has to be its own bean, not a private method on HashtagService, since Spring
 * can't apply REQUIRES_NEW to a self-invoked call — the same reason NotificationConsumer's DB write
 * is delegated to NotificationWriter.
 */
@Component
class HashtagCreator {

    private final HashtagRepository hashtagRepository;

    HashtagCreator(HashtagRepository hashtagRepository) {
        this.hashtagRepository = hashtagRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Hashtag create(String tag) {
        Hashtag hashtag = new Hashtag();
        hashtag.setTag(tag);
        return hashtagRepository.saveAndFlush(hashtag);
    }
}
