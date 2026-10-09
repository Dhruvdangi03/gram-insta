package com.instaclone.hashtag.service;

import com.instaclone.common.util.TextEntities;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the post_hashtags index in step with post captions. Separate from HashtagService (which
 * reads) because PostService/ReelService depend on this one, while HashtagService depends on
 * PostService — merging them would be a circular dependency. Joins the caller's transaction, so
 * the index and the caption always commit or roll back together.
 */
@Service
public class HashtagIndexService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void reindex(Long postId, String caption) {
        entityManager
                .createNativeQuery("DELETE FROM post_hashtags WHERE post_id = :postId")
                .setParameter("postId", postId)
                .executeUpdate();
        Set<String> tags = TextEntities.extractHashtags(caption);
        for (String tag : tags) {
            entityManager
                    .createNativeQuery(
                            "INSERT INTO post_hashtags (post_id, tag) VALUES (:postId, :tag) ON CONFLICT DO NOTHING")
                    .setParameter("postId", postId)
                    .setParameter("tag", tag)
                    .executeUpdate();
        }
    }
}
