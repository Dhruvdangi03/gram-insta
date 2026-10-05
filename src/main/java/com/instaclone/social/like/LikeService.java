package com.instaclone.social.like;

import com.instaclone.common.ForbiddenException;
import com.instaclone.common.NotFoundException;
import com.instaclone.notification.NotificationEvent;
import com.instaclone.notification.NotificationType;
import com.instaclone.post.Post;
import com.instaclone.post.PostRepository;
import com.instaclone.social.moderation.ModerationService;
import com.instaclone.user.ProfileVisibilityService;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LikeService {

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ProfileVisibilityService profileVisibilityService;
    private final ModerationService moderationService;
    private final ApplicationEventPublisher eventPublisher;

    public LikeService(
            LikeRepository likeRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            ProfileVisibilityService profileVisibilityService,
            ModerationService moderationService,
            ApplicationEventPublisher eventPublisher) {
        this.likeRepository = likeRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.profileVisibilityService = profileVisibilityService;
        this.moderationService = moderationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public LikeCountResponse likePost(Long userId, Long postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        User viewer = userRepository.getReferenceById(userId);
        assertVisible(post, viewer);

        boolean alreadyLiked =
                likeRepository.existsByUserIdAndLikeableTypeAndLikeableId(userId, LikeableType.POST, postId);
        if (!alreadyLiked) {
            Like like = new Like();
            like.setUser(viewer);
            like.setLikeableType(LikeableType.POST);
            like.setLikeableId(postId);
            like.setCreatedAt(Instant.now());
            likeRepository.save(like);

            postRepository.incrementLikeCount(postId);

            Long recipientId = post.getUser().getId();
            if (!recipientId.equals(userId)) {
                eventPublisher.publishEvent(
                        new NotificationEvent(recipientId, userId, NotificationType.LIKE, "POST", postId));
            }
        }

        return new LikeCountResponse(alreadyLiked ? post.getLikeCount() : post.getLikeCount() + 1, true);
    }

    @Transactional
    public LikeCountResponse unlikePost(Long userId, Long postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        User viewer = userRepository.getReferenceById(userId);
        assertVisible(post, viewer);

        boolean removed = likeRepository
                .findByUserIdAndLikeableTypeAndLikeableId(userId, LikeableType.POST, postId)
                .map(like -> {
                    likeRepository.delete(like);
                    return true;
                })
                .orElse(false);
        if (removed) {
            postRepository.decrementLikeCount(postId);
        }

        return new LikeCountResponse(removed ? Math.max(0, post.getLikeCount() - 1) : post.getLikeCount(), false);
    }

    private void assertVisible(Post post, User viewer) {
        if (!profileVisibilityService.isVisible(post.getUser(), viewer)) {
            throw new ForbiddenException("This account is private");
        }
        // isVisible is deliberately directional (lets a blocker still view the blocked account's
        // profile to reach Unblock) — but liking/commenting is a write, so it must also check the
        // blocker's own side, which isVisible alone doesn't cover.
        if (moderationService.isBlockedEitherDirection(viewer.getId(), post.getUser().getId())) {
            throw new ForbiddenException("You can't interact with this account");
        }
    }
}
