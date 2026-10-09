package com.instaclone.social.block.repository;

import com.instaclone.social.block.entity.UserBlock;
import com.instaclone.user.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @Modifying
    @Query("delete from UserBlock b where b.blocker.id = :blockerId and b.blocked.id = :blockedId")
    void deleteByPair(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    /** True if either user has blocked the other — a block hides both accounts from each other. */
    @Query(
            value = "SELECT EXISTS (SELECT 1 FROM user_blocks WHERE (blocker_id = :a AND blocked_id = :b) "
                    + "OR (blocker_id = :b AND blocked_id = :a))",
            nativeQuery = true)
    boolean existsEitherWay(@Param("a") Long a, @Param("b") Long b);

    /** Every user the viewer has blocked or who has blocked the viewer. */
    @Query(
            value = "SELECT blocked_id FROM user_blocks WHERE blocker_id = :userId "
                    + "UNION SELECT blocker_id FROM user_blocks WHERE blocked_id = :userId",
            nativeQuery = true)
    List<Long> findBlockRelatedUserIds(@Param("userId") Long userId);

    /** Accounts the user has blocked, newest first. Short, unpaginated list — same precedent as
     * UserRepository.findSuggestions. */
    @Query(
            value = "SELECT u.* FROM users u JOIN user_blocks b ON b.blocked_id = u.id "
                    + "WHERE b.blocker_id = :blockerId ORDER BY b.created_at DESC, b.id DESC LIMIT :limit",
            nativeQuery = true)
    List<User> findBlockedUsers(@Param("blockerId") Long blockerId, @Param("limit") int limit);
}
