package com.instaclone.post.repository;

import com.instaclone.post.entity.Post;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("select p.id from Post p where p.user.id = :userId")
    List<Long> findIdsByUserId(@Param("userId") Long userId);

    @Query("select coalesce(sum(p.likeCount), 0) from Post p where p.user.id = :userId")
    long sumLikeCountByUserId(@Param("userId") Long userId);

    @Query("select coalesce(sum(p.commentCount), 0) from Post p where p.user.id = :userId")
    long sumCommentCountByUserId(@Param("userId") Long userId);

    // Post deletion, done as direct SQL so it never depends on loading entities. likes and
    // notifications reference their target by (type, id) with no foreign key, so they are not
    // cascaded by the database and must be removed explicitly — including those pointing at the
    // post's comments, which are about to be cascade-deleted. Call these in this order inside one
    // transaction, before deletePostById (the comment sub-selects need the comments to still exist).
    @Modifying
    @Query(
            value = "DELETE FROM likes WHERE (likeable_type = 'POST' AND likeable_id = :postId) "
                    + "OR (likeable_type = 'COMMENT' AND likeable_id IN (SELECT id FROM comments WHERE post_id = :postId))",
            nativeQuery = true)
    void deleteLikesForPost(@Param("postId") Long postId);

    @Modifying
    @Query(
            value = "DELETE FROM notifications WHERE (target_type = 'POST' AND target_id = :postId) "
                    + "OR (target_type = 'COMMENT' AND target_id IN (SELECT id FROM comments WHERE post_id = :postId))",
            nativeQuery = true)
    void deleteNotificationsForPost(@Param("postId") Long postId);

    // media, comments and saved_posts rows go with it via ON DELETE CASCADE.
    @Modifying
    @Query(value = "DELETE FROM posts WHERE id = :postId", nativeQuery = true)
    void deletePostById(@Param("postId") Long postId);

    // Atomic SQL increments/decrements, not read-modify-write on the entity — two concurrent
    // likes/comments both reading like_count=5 and writing 6 would otherwise lose one update.
    @Modifying
    @Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :postId")
    void incrementLikeCount(@Param("postId") Long postId);

    @Modifying
    @Query("update Post p set p.likeCount = case when p.likeCount > 0 then p.likeCount - 1 else 0 end where p.id = :postId")
    void decrementLikeCount(@Param("postId") Long postId);

    @Modifying
    @Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :postId")
    void incrementCommentCount(@Param("postId") Long postId);

    @Modifying
    @Query(
            "update Post p set p.commentCount = case when p.commentCount > :amount then p.commentCount - :amount else 0 end "
                    + "where p.id = :postId")
    void decrementCommentCountBy(@Param("postId") Long postId, @Param("amount") long amount);

    // "type != 'REEL' OR media is READY" — a still-transcoding reel has no playable url yet, so it
    // must stay out of every listing until the async worker flips its media row to READY. Photos
    // (never REEL) are unaffected and always pass this check. Two copies (unaliased "posts" table
    // vs. aliased "p") because annotation values must be compile-time constants, so this can't be
    // built with a runtime String.replace() call.
    String READY_FILTER = "(type != 'REEL' OR EXISTS (SELECT 1 FROM media m WHERE m.post_id = posts.id AND m.status = 'READY'))";
    String READY_FILTER_P = "(p.type != 'REEL' OR EXISTS (SELECT 1 FROM media m WHERE m.post_id = p.id AND m.status = 'READY'))";

    // Must respect READY_FILTER like every grid/feed query — otherwise a still-transcoding or
    // failed reel inflates the publicly-shown post count without ever rendering a tile anywhere.
    @Query(value = "SELECT COUNT(*) FROM posts WHERE user_id = :userId AND " + READY_FILTER, nativeQuery = true)
    long countByUserId(@Param("userId") Long userId);

    @Query(
            value = "SELECT * FROM posts WHERE user_id = :userId AND " + READY_FILTER
                    + " ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findFirstPageByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT * FROM posts WHERE user_id = :userId AND (created_at, id) < (:cursorCreatedAt, :cursorId) "
                            + "AND " + READY_FILTER + " ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findPageByUserIdAfterCursor(
            @Param("userId") Long userId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    // No READY_FILTER — used only for a user viewing their OWN profile grid, where a still-
    // transcoding or failed reel should still show up (so it's at least discoverable/deletable)
    // instead of being invisible even to its own uploader.
    @Query(
            value = "SELECT * FROM posts WHERE user_id = :userId ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findFirstPageByUserIdIncludingPending(@Param("userId") Long userId, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT * FROM posts WHERE user_id = :userId AND (created_at, id) < (:cursorCreatedAt, :cursorId) "
                            + "ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findPageByUserIdAfterCursorIncludingPending(
            @Param("userId") Long userId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    // excludedPostIds (see FeedService) is filtered here in the query itself, not after the
    // fetch, so the limit+1 lookahead CursorPage.of relies on to compute hasMore stays accurate —
    // filtering after the fetch could shrink a full lookahead window down to <= limit and make a
    // feed with more pages look like it had reached the end.
    @Query(
            value = "SELECT * FROM posts WHERE user_id IN (:userIds) AND id NOT IN (:excludedPostIds) AND "
                    + READY_FILTER
                    + " ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findFirstPageByUserIds(
            @Param("userIds") List<Long> userIds,
            @Param("excludedPostIds") List<Long> excludedPostIds,
            @Param("limit") int limit);

    @Query(
            value =
                    "SELECT * FROM posts WHERE user_id IN (:userIds) AND id NOT IN (:excludedPostIds) "
                            + "AND (created_at, id) < (:cursorCreatedAt, :cursorId) "
                            + "AND " + READY_FILTER + " ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findPageByUserIdsAfterCursor(
            @Param("userIds") List<Long> userIds,
            @Param("excludedPostIds") List<Long> excludedPostIds,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    // Reels from the given users (followed accounts + the viewer) PLUS every public account's reels —
    // the Reels tab is a discovery surface. Restricting it to followed accounts left a reel invisible
    // to everyone except its uploader (who always sees their own). Private accounts' reels still
    // require a follow.
    @Query(
            value = "SELECT p.* FROM posts p JOIN users u ON u.id = p.user_id "
                    + "WHERE p.type = 'REEL' AND (p.user_id IN (:userIds) OR u.is_private = false) "
                    + "AND p.user_id NOT IN (:excludedIds) AND "
                    + READY_FILTER_P
                    + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findFirstReelsPageByUserIds(
            @Param("userIds") List<Long> userIds, @Param("excludedIds") List<Long> excludedIds, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT p.* FROM posts p JOIN users u ON u.id = p.user_id "
                            + "WHERE p.type = 'REEL' AND (p.user_id IN (:userIds) OR u.is_private = false) "
                            + "AND p.user_id NOT IN (:excludedIds) "
                            + "AND (p.created_at, p.id) < (:cursorCreatedAt, :cursorId) AND " + READY_FILTER_P
                            + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findReelsPageByUserIdsAfterCursor(
            @Param("userIds") List<Long> userIds,
            @Param("excludedIds") List<Long> excludedIds,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    // Explore: public accounts only (never a private account regardless of follow state), excluding
    // the viewer and anyone they already follow, ranked by like_count over the trailing window.
    // excludedIds must always include the viewer's own id (see FeedService) so this NOT IN never
    // receives an empty list, which native Postgres rejects as invalid syntax.
    @Query(
            value =
                    "SELECT p.* FROM posts p JOIN users u ON u.id = p.user_id "
                            + "WHERE u.is_private = false AND p.user_id NOT IN (:excludedIds) "
                            + "AND p.id NOT IN (:excludedPostIds) "
                            + "AND p.created_at > :since AND "
                            + READY_FILTER_P
                            + " ORDER BY p.like_count DESC, p.id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findExploreFirstPage(
            @Param("excludedIds") List<Long> excludedIds,
            @Param("excludedPostIds") List<Long> excludedPostIds,
            @Param("since") Instant since,
            @Param("limit") int limit);

    @Query(
            value =
                    "SELECT p.* FROM posts p JOIN users u ON u.id = p.user_id "
                            + "WHERE u.is_private = false AND p.user_id NOT IN (:excludedIds) "
                            + "AND p.id NOT IN (:excludedPostIds) "
                            + "AND p.created_at > :since AND (p.like_count, p.id) < (:cursorRank, :cursorId) AND "
                            + READY_FILTER_P
                            + " ORDER BY p.like_count DESC, p.id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findExploreAfterCursor(
            @Param("excludedIds") List<Long> excludedIds,
            @Param("excludedPostIds") List<Long> excludedPostIds,
            @Param("since") Instant since,
            @Param("cursorRank") long cursorRank,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    // Posts carrying a hashtag (see HashtagIndexService): public accounts only, like Explore, and
    // excludedIds (blocks; never empty, see HashtagService) keeps either side of a block out.
    @Query(
            value = "SELECT p.* FROM posts p JOIN users u ON u.id = p.user_id "
                    + "JOIN post_hashtags h ON h.post_id = p.id "
                    + "WHERE h.tag = :tag AND u.is_private = false AND p.user_id NOT IN (:excludedIds) AND "
                    + READY_FILTER_P
                    + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findByHashtagFirstPage(
            @Param("tag") String tag, @Param("excludedIds") List<Long> excludedIds, @Param("limit") int limit);

    @Query(
            value = "SELECT p.* FROM posts p JOIN users u ON u.id = p.user_id "
                    + "JOIN post_hashtags h ON h.post_id = p.id "
                    + "WHERE h.tag = :tag AND u.is_private = false AND p.user_id NOT IN (:excludedIds) "
                    + "AND (p.created_at, p.id) < (:cursorCreatedAt, :cursorId) AND "
                    + READY_FILTER_P
                    + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> findByHashtagAfterCursor(
            @Param("tag") String tag,
            @Param("excludedIds") List<Long> excludedIds,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    // Plain ILIKE search over caption/author username. Same discovery-surface rule as explore:
    // public accounts only, regardless of follow state, and the usual READY_FILTER_P so a
    // still-transcoding reel never surfaces. excludedIds always includes the viewer's own id (see
    // SearchService) so NOT IN never receives an empty list. A "#tag" query still finds posts
    // whose caption contains that text, since the caption itself is matched.
    @Query(
            value = "SELECT p.* FROM posts p "
                    + "JOIN users u ON u.id = p.user_id "
                    + "WHERE u.is_private = false AND p.user_id NOT IN (:excludedIds) "
                    + "AND (p.caption ILIKE :pattern OR u.username ILIKE :pattern) "
                    + "AND " + READY_FILTER_P
                    + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit",
            nativeQuery = true)
    List<Post> searchByCaptionOrAuthor(
            @Param("pattern") String pattern,
            @Param("excludedIds") List<Long> excludedIds,
            @Param("limit") int limit);

    default List<Post> searchByQuery(String query, List<Long> excludedIds, int limit) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return searchByCaptionOrAuthor("%" + trimmed + "%", excludedIds, limit);
    }
}
