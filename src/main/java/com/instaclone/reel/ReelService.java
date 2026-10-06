package com.instaclone.reel;

import com.instaclone.common.BadRequestException;
import com.instaclone.common.Cursor;
import com.instaclone.common.CursorPage;
import com.instaclone.common.NotFoundException;
import com.instaclone.config.StorageProperties;
import com.instaclone.media.MediaUploadedEvent;
import com.instaclone.post.Media;
import com.instaclone.post.MediaRepository;
import com.instaclone.post.MediaStatus;
import com.instaclone.post.MediaType;
import com.instaclone.post.Post;
import com.instaclone.post.PostRepository;
import com.instaclone.post.PostResponse;
import com.instaclone.post.PostService;
import com.instaclone.post.PostType;
import com.instaclone.social.follow.FollowRepository;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import com.instaclone.user.UserSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reels are posts with type=REEL — same posts/media tables and feed machinery as photos, per the
 * build doc's "Reels are posts... matching how Instagram unified its feed content types." The only
 * genuinely new behavior here is that the media starts PENDING and gets transcoded asynchronously
 * (see the com.instaclone.media package) instead of being immediately READY like a photo.
 */
@Service
public class ReelService {

    private final PostRepository postRepository;
    private final MediaRepository mediaRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PostService postService;
    private final StorageProperties storageProperties;
    private final ApplicationEventPublisher eventPublisher;

    public ReelService(
            PostRepository postRepository,
            MediaRepository mediaRepository,
            UserRepository userRepository,
            FollowRepository followRepository,
            PostService postService,
            StorageProperties storageProperties,
            ApplicationEventPublisher eventPublisher) {
        this.postRepository = postRepository;
        this.mediaRepository = mediaRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.postService = postService;
        this.storageProperties = storageProperties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PostResponse createReel(Long userId, CreateReelRequest request) {
        User author = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));

        String mediaUrl = request.media().url();
        if (!storageProperties.isOwnedUrl(mediaUrl, userId)) {
            throw new BadRequestException("Media url must reference an object uploaded via /posts/upload-url");
        }
        String sourceObjectKey = storageProperties.objectKeyFromPublicUrl(mediaUrl);

        Post post = new Post();
        post.setUser(author);
        post.setCaption(request.caption());
        post.setLocation(request.location());
        post.setType(PostType.REEL);
        post.setMediaCount(1);
        post.setCreatedAt(Instant.now());
        post = postRepository.save(post);

        Media media = new Media();
        media.setPost(post);
        media.setUrl(mediaUrl);
        media.setMediaType(MediaType.VIDEO);
        media.setPosition(0);
        media.setStatus(MediaStatus.PENDING);
        media = mediaRepository.save(media);

        // Only takes effect after this transaction commits — see MediaStreamPublisher. Search
        // indexing happens once transcoding actually finishes (MediaUploadConsumer), not here —
        // the media is still PENDING at this point, and every other listing hides a still-
        // transcoding reel behind PostRepository.READY_FILTER; search must respect the same rule.
        eventPublisher.publishEvent(new MediaUploadedEvent(media.getId(), post.getId(), sourceObjectKey, userId));

        return postService.toResponse(post, UserSummary.from(author), List.of(media), false, false);
    }

    /** Follows-based, mirroring FeedService.getHomeFeed exactly but filtered to type=REEL + status=READY.
     * Always includes the viewer's own reels too — otherwise a user who just posted a reel would
     * never see it in this feed at all, since they don't "follow" themselves. */
    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getReelsFeed(Long viewerId, String cursor, int limit) {
        List<Long> followedIds = new ArrayList<>(followRepository.findAcceptedFolloweeIds(viewerId));
        followedIds.add(viewerId);

        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<Post> rows = decoded == null
                ? postRepository.findFirstReelsPageByUserIds(followedIds, limit + 1)
                : postRepository.findReelsPageByUserIdsAfterCursor(followedIds, decoded.createdAt(), decoded.id(), limit + 1);

        return postService.toPage(rows, limit, viewerId);
    }
}
