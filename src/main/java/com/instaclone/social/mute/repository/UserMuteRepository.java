package com.instaclone.social.mute.repository;

import com.instaclone.social.mute.entity.UserMute;
import com.instaclone.user.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserMuteRepository extends JpaRepository<UserMute, Long> {

    boolean existsByMuterIdAndMutedId(Long muterId, Long mutedId);

    @Modifying
    @Query("delete from UserMute m where m.muter.id = :muterId and m.muted.id = :mutedId")
    void deleteByPair(@Param("muterId") Long muterId, @Param("mutedId") Long mutedId);

    @Query(value = "SELECT muted_id FROM user_mutes WHERE muter_id = :muterId", nativeQuery = true)
    List<Long> findMutedIds(@Param("muterId") Long muterId);

    @Query(
            value = "SELECT u.* FROM users u JOIN user_mutes m ON m.muted_id = u.id "
                    + "WHERE m.muter_id = :muterId ORDER BY m.created_at DESC, m.id DESC LIMIT :limit",
            nativeQuery = true)
    List<User> findMutedUsers(@Param("muterId") Long muterId, @Param("limit") int limit);
}
