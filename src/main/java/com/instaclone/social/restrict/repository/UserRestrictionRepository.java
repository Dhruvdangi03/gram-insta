package com.instaclone.social.restrict.repository;

import com.instaclone.social.restrict.entity.UserRestriction;
import com.instaclone.user.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRestrictionRepository extends JpaRepository<UserRestriction, Long> {

    boolean existsByRestrictorIdAndRestrictedId(Long restrictorId, Long restrictedId);

    @Modifying
    @Query("delete from UserRestriction r where r.restrictor.id = :restrictorId and r.restricted.id = :restrictedId")
    void deleteByPair(@Param("restrictorId") Long restrictorId, @Param("restrictedId") Long restrictedId);

    @Query(
            value = "SELECT u.* FROM users u JOIN user_restrictions r ON r.restricted_id = u.id "
                    + "WHERE r.restrictor_id = :restrictorId ORDER BY r.created_at DESC, r.id DESC LIMIT :limit",
            nativeQuery = true)
    List<User> findRestrictedUsers(@Param("restrictorId") Long restrictorId, @Param("limit") int limit);
}
