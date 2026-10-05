package com.instaclone.social.moderation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserModerationRepository extends JpaRepository<UserModeration, Long> {

    Optional<UserModeration> findByActorIdAndTargetIdAndType(Long actorId, Long targetId, ModerationType type);

    boolean existsByActorIdAndTargetIdAndType(Long actorId, Long targetId, ModerationType type);

    @Query(
            "select (count(m) > 0) from UserModeration m where m.type = com.instaclone.social.moderation.ModerationType.BLOCK "
                    + "and ((m.actor.id = :aId and m.target.id = :bId) or (m.actor.id = :bId and m.target.id = :aId))")
    boolean existsBlockEitherDirection(@Param("aId") Long aId, @Param("bId") Long bId);

    /** Every id that has blocked, or been blocked by, userId — for excluding both directions from discovery surfaces. */
    @Query(
            "select case when m.actor.id = :userId then m.target.id else m.actor.id end from UserModeration m "
                    + "where m.type = com.instaclone.social.moderation.ModerationType.BLOCK "
                    + "and (m.actor.id = :userId or m.target.id = :userId)")
    List<Long> findBlockedEitherDirectionIds(@Param("userId") Long userId);
}
