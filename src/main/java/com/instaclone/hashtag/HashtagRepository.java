package com.instaclone.hashtag;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HashtagRepository extends JpaRepository<Hashtag, Long> {
    Optional<Hashtag> findByTag(String tag);
}
