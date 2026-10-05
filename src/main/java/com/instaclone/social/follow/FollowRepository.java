package com.instaclone.social.follow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    boolean existsByFollowerIdAndFolloweeIdAndStatus(Long followerId, Long followeeId, FollowStatus status);

    long countByFolloweeIdAndStatus(Long followeeId, FollowStatus status);

    long countByFollowerIdAndStatus(Long followerId, FollowStatus status);

    /** Auto-approves every outstanding request when an account switches from private to public —
     * its content is now visible to everyone anyway, so a still-PENDING row would otherwise strand
     * the requester on "Requested" even though nothing is actually gating them anymore. */
    @Modifying
    @Query("update Follow f set f.status = com.instaclone.social.follow.FollowStatus.ACCEPTED "
            + "where f.followee.id = :userId and f.status = com.instaclone.social.follow.FollowStatus.PENDING")
    void acceptAllPendingForFollowee(@Param("userId") Long userId);

    @Query(
            value = "SELECT followee_id FROM follows WHERE follower_id = :followerId AND status = 'ACCEPTED'",
            nativeQuery = true)
    List<Long> findAcceptedFolloweeIds(@Param("followerId") Long followerId);

    /** Both ACCEPTED and PENDING — for excluding accounts already followed or already requested
     * from "suggested for you," not just accepted follows. */
    @Query(value = "SELECT followee_id FROM follows WHERE follower_id = :followerId", nativeQuery = true)
    List<Long> findAllFolloweeIds(@Param("followerId") Long followerId);

    @Query(
            value =
                    "SELECT f.id AS followId, f.created_at AS followCreatedAt, u.id AS userId, u.username AS username, "
                            + "u.full_name AS fullName, u.profile_picture_url AS profilePictureUrl, u.is_verified AS isVerified "
                            + "FROM follows f JOIN users u ON u.id = f.follower_id "
                            + "WHERE f.followee_id = :userId AND f.status = 'ACCEPTED' "
                            + "ORDER BY f.created_at DESC, f.id DESC LIMIT :limit",
            nativeQuery = true)
    List<FollowUserRow> findFirstPageFollowers(@Param("userId") Long userId, @Param("limit") int limit);

    /** Incoming PENDING follow requests on a private account — no cursor pagination, matching the
     * short-list precedent findSuggestions already set for a similarly small, unbounded-in-practice
     * list. There was previously no way for an account owner to even see these existed. */
    @Query(
            value =
                    "SELECT f.id AS followId, f.created_at AS followCreatedAt, u.id AS userId, u.username AS username, "
                            + "u.full_name AS fullName, u.profile_picture_url AS profilePictureUrl, u.is_verified AS isVerified "
                            + "FROM follows f JOIN users u ON u.id = f.follower_id "
                            + "WHERE f.followee_id = :userId AND f.status = 'PENDING' "
                            + "ORDER BY f.created_at DESC, f.id DESC LIMIT :limit",
            nativeQuery = true)
    List<FollowUserRow> findPendingFollowRequests(@Param("userId") Long userId, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT f.id AS followId, f.created_at AS followCreatedAt, u.id AS userId, u.username AS username, "
                            + "u.full_name AS fullName, u.profile_picture_url AS profilePictureUrl, u.is_verified AS isVerified "
                            + "FROM follows f JOIN users u ON u.id = f.follower_id "
                            + "WHERE f.followee_id = :userId AND f.status = 'ACCEPTED' "
                            + "AND (f.created_at, f.id) < (:cursorCreatedAt, :cursorId) "
                            + "ORDER BY f.created_at DESC, f.id DESC LIMIT :limit",
            nativeQuery = true)
    List<FollowUserRow> findPageFollowersAfterCursor(
            @Param("userId") Long userId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    @Query(
            value =
                    "SELECT f.id AS followId, f.created_at AS followCreatedAt, u.id AS userId, u.username AS username, "
                            + "u.full_name AS fullName, u.profile_picture_url AS profilePictureUrl, u.is_verified AS isVerified "
                            + "FROM follows f JOIN users u ON u.id = f.followee_id "
                            + "WHERE f.follower_id = :userId AND f.status = 'ACCEPTED' "
                            + "ORDER BY f.created_at DESC, f.id DESC LIMIT :limit",
            nativeQuery = true)
    List<FollowUserRow> findFirstPageFollowing(@Param("userId") Long userId, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT f.id AS followId, f.created_at AS followCreatedAt, u.id AS userId, u.username AS username, "
                            + "u.full_name AS fullName, u.profile_picture_url AS profilePictureUrl, u.is_verified AS isVerified "
                            + "FROM follows f JOIN users u ON u.id = f.followee_id "
                            + "WHERE f.follower_id = :userId AND f.status = 'ACCEPTED' "
                            + "AND (f.created_at, f.id) < (:cursorCreatedAt, :cursorId) "
                            + "ORDER BY f.created_at DESC, f.id DESC LIMIT :limit",
            nativeQuery = true)
    List<FollowUserRow> findPageFollowingAfterCursor(
            @Param("userId") Long userId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);
}
