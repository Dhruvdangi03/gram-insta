package com.instaclone.story.job;

import com.instaclone.story.repository.StoryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Storage hygiene only — not what makes a story disappear (every read query in StoryRepository
 * already filters expires_at > now directly, so that's immediate). This just hard-deletes rows
 * that expired a while ago so the table doesn't grow forever. A day of slack after expiry, purely
 * so a just-expired story isn't yanked out from under some other in-flight read.
 */
@Component
public class StoryCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(StoryCleanupJob.class);
    private static final Duration RETENTION_AFTER_EXPIRY = Duration.ofDays(1);

    private final StoryRepository storyRepository;

    public StoryCleanupJob(StoryRepository storyRepository) {
        this.storyRepository = storyRepository;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void purgeExpiredStories() {
        int deleted = storyRepository.deleteByExpiresAtBefore(Instant.now().minus(RETENTION_AFTER_EXPIRY));
        if (deleted > 0) {
            log.info("Purged {} expired stories", deleted);
        }
    }
}
