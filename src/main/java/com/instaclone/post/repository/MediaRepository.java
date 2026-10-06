package com.instaclone.post.repository;

import com.instaclone.post.entity.Media;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRepository extends JpaRepository<Media, Long> {
    List<Media> findByPostIdOrderByPosition(Long postId);

    List<Media> findByPostIdInOrderByPostIdAscPositionAsc(List<Long> postIds);
}
