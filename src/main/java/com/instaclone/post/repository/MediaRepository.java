package com.instaclone.post.repository;

import com.instaclone.post.entity.Media;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MediaRepository extends JpaRepository<Media, Long> {
    List<Media> findByPostIdOrderByPosition(Long postId);

    List<Media> findByPostIdInOrderByPostIdAscPositionAsc(List<Long> postIds);

    @Query("select m from Media m join fetch m.post p join fetch p.user "
            + "where m.mediaType = com.instaclone.post.enums.MediaType.VIDEO "
            + "and m.status <> com.instaclone.post.enums.MediaStatus.READY")
    List<Media> findUnfinishedVideos();
}
