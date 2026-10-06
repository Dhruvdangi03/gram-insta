package com.instaclone.social.like.repository;

import com.instaclone.social.like.entity.Like;
import com.instaclone.social.like.enums.LikeableType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<Like, Long> {

    Optional<Like> findByUserIdAndLikeableTypeAndLikeableId(Long userId, LikeableType likeableType, Long likeableId);

    boolean existsByUserIdAndLikeableTypeAndLikeableId(Long userId, LikeableType likeableType, Long likeableId);

    void deleteByLikeableTypeAndLikeableId(LikeableType likeableType, Long likeableId);

    void deleteByLikeableTypeAndLikeableIdIn(LikeableType likeableType, List<Long> likeableIds);

    void deleteByUserIdAndLikeableTypeAndLikeableId(Long userId, LikeableType likeableType, Long likeableId);

    @org.springframework.data.jpa.repository.Query(
            "select l.likeableId from Like l where l.user.id = :userId and l.likeableType = :type and l.likeableId in :ids")
    List<Long> findLikedIds(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("type") LikeableType type,
            @org.springframework.data.repository.query.Param("ids") List<Long> ids);
}
