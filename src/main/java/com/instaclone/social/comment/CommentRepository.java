package com.instaclone.social.comment;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("select c.id from Comment c where c.parent.id = :parentId")
    List<Long> findReplyIdsByParentId(@Param("parentId") Long parentId);

    @Query("select c.id from Comment c where c.post.id = :postId")
    List<Long> findIdsByPostId(@Param("postId") Long postId);

    List<Comment> findByUserIdAndPostIdIn(Long userId, List<Long> postIds);

    @Query(
            value = "SELECT * FROM comments WHERE post_id = :postId ORDER BY created_at ASC, id ASC LIMIT :limit",
            nativeQuery = true)
    List<Comment> findFirstPageByPostId(@Param("postId") Long postId, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT * FROM comments WHERE post_id = :postId AND (created_at, id) > (:cursorCreatedAt, :cursorId) "
                            + "ORDER BY created_at ASC, id ASC LIMIT :limit",
            nativeQuery = true)
    List<Comment> findPageByPostIdAfterCursor(
            @Param("postId") Long postId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);
}
