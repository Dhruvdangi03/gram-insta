package com.instaclone.story;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryRepository extends JpaRepository<Story, Long> {

    // A single author's tray — naturally bounded (stories auto-expire within a day), so unlike
    // the accumulate-forever lists elsewhere in this codebase, a plain unpaginated list is fine.
    // Ascending (oldest first), not the DESC "latest first" convention used for paginated lists
    // elsewhere — a story tray is watched in the order it was posted, like a narrative, not
    // browsed newest-first like a feed.
    @Query(
            value = "SELECT * FROM stories WHERE user_id = :userId AND expires_at > :now ORDER BY created_at ASC",
            nativeQuery = true)
    List<Story> findActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    @Query(
            value =
                    "SELECT * FROM stories WHERE user_id IN (:userIds) AND expires_at > :now "
                            + "ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Story> findFirstActivePageByUserIds(
            @Param("userIds") List<Long> userIds, @Param("now") Instant now, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT * FROM stories WHERE user_id IN (:userIds) AND expires_at > :now "
                            + "AND (created_at, id) < (:cursorCreatedAt, :cursorId) "
                            + "ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Story> findActivePageByUserIdsAfterCursor(
            @Param("userIds") List<Long> userIds,
            @Param("now") Instant now,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    // Storage hygiene only — a story is already gone from every read above the moment expires_at
    // passes (a plain WHERE filter, evaluated immediately, no polling lag). This just reclaims
    // space for rows that expired a while ago.
    @Modifying
    @Query(value = "DELETE FROM stories WHERE expires_at < :cutoff", nativeQuery = true)
    int deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);
}
