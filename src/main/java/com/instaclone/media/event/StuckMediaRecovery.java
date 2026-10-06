package com.instaclone.media.event;

import com.instaclone.post.entity.Media;
import com.instaclone.post.repository.MediaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-shot, at startup: re-queues reels whose video never finished transcoding (still PENDING /
 * PROCESSING because the app restarted mid-job, or FAILED from an earlier bug). Until they are
 * READY they are visible only to their uploader. Runs once per boot — not on a timer — so a file
 * that genuinely can't be transcoded costs one extra attempt per deploy, never a retry loop.
 * While a video isn't READY its url is still the raw upload, which is what we re-transcode.
 */
@Component
public class StuckMediaRecovery {

    private static final Logger log = LoggerFactory.getLogger(StuckMediaRecovery.class);

    private final MediaRepository mediaRepository;
    private final ApplicationEventPublisher eventPublisher;

    public StuckMediaRecovery(MediaRepository mediaRepository, ApplicationEventPublisher eventPublisher) {
        this.mediaRepository = mediaRepository;
        this.eventPublisher = eventPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void requeueUnfinishedVideos() {
        for (Media media : mediaRepository.findUnfinishedVideos()) {
            String url = media.getUrl();
            String publicBase = "/posts/" + media.getPost().getUser().getId() + "/";
            int idx = url.indexOf(publicBase);
            if (idx < 0 || url.contains("_720p")) {
                continue;
            }
            String sourceObjectKey = url.substring(idx + 1);
            log.info("Re-queueing unfinished video: media {} ({})", media.getId(), sourceObjectKey);
            eventPublisher.publishEvent(new MediaUploadedEvent(
                    media.getId(), media.getPost().getId(), sourceObjectKey, media.getPost().getUser().getId()));
        }
    }
}
