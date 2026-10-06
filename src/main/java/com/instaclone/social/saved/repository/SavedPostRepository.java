package com.instaclone.social.saved.repository;

import com.instaclone.social.saved.entity.SavedPost;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedPostRepository extends JpaRepository<SavedPost, Long> {

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    Optional<SavedPost> findByUserIdAndPostId(Long userId, Long postId);

    @Query("select sp.post.id from SavedPost sp where sp.user.id = :userId and sp.post.id in :postIds")
    List<Long> findSavedPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);

    @Query(
            value = "SELECT * FROM saved_posts WHERE user_id = :userId ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<SavedPost> findFirstPage(@Param("userId") Long userId, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT * FROM saved_posts WHERE user_id = :userId AND (created_at, id) < (:cursorCreatedAt, :cursorId) "
                            + "ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<SavedPost> findPageAfterCursor(
            @Param("userId") Long userId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);
}
