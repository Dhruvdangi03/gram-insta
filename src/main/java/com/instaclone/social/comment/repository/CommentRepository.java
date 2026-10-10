package com.instaclone.social.comment.repository;

import com.instaclone.social.comment.entity.Comment;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Mirrors PostRepository's increment/decrement pattern: an atomic UPDATE rather than
    // read-modify-write, so two concurrent likes on the same comment don't lose an update.
    @Modifying
    @Query("update Comment c set c.likeCount = c.likeCount + 1 where c.id = :commentId")
    void incrementLikeCount(@Param("commentId") Long commentId);

    @Modifying
    @Query(
            "update Comment c set c.likeCount = case when c.likeCount > 0 then c.likeCount - 1 else 0 end where c.id = :commentId")
    void decrementLikeCount(@Param("commentId") Long commentId);

    @Query("select c.id from Comment c where c.parent.id = :parentId")
    List<Long> findReplyIdsByParentId(@Param("parentId") Long parentId);

    @Query("select c.id from Comment c where c.post.id = :postId")
    List<Long> findIdsByPostId(@Param("postId") Long postId);

    List<Comment> findByUserIdAndPostIdIn(Long userId, List<Long> postIds);

    @Query("select count(c) from Comment c where c.parent.id = :parentId and c.approved = true")
    long countApprovedRepliesByParentId(@Param("parentId") Long parentId);

    // Unapproved (restricted-user) comments are visible only to their author and the post owner.
    // Filtered in SQL, not after the fetch, so pages stay full and the cursor stays correct.
    @Query(
            value = "SELECT c.* FROM comments c JOIN posts p ON p.id = c.post_id WHERE c.post_id = :postId "
                    + "AND (c.approved OR c.user_id = :viewerId OR p.user_id = :viewerId) "
                    + "ORDER BY c.created_at ASC, c.id ASC LIMIT :limit",
            nativeQuery = true)
    List<Comment> findFirstPageByPostId(
            @Param("postId") Long postId, @Param("viewerId") Long viewerId, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT c.* FROM comments c JOIN posts p ON p.id = c.post_id WHERE c.post_id = :postId "
                            + "AND (c.approved OR c.user_id = :viewerId OR p.user_id = :viewerId) "
                            + "AND (c.created_at, c.id) > (:cursorCreatedAt, :cursorId) "
                            + "ORDER BY c.created_at ASC, c.id ASC LIMIT :limit",
            nativeQuery = true)
    List<Comment> findPageByPostIdAfterCursor(
            @Param("postId") Long postId,
            @Param("viewerId") Long viewerId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);
}
