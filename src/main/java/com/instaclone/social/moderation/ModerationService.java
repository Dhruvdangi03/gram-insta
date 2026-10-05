package com.instaclone.social.moderation;

import com.instaclone.common.BadRequestException;
import com.instaclone.common.NotFoundException;
import com.instaclone.notification.FollowRequestNotificationCleaner;
import com.instaclone.post.PostRepository;
import com.instaclone.social.comment.Comment;
import com.instaclone.social.comment.CommentRepository;
import com.instaclone.social.follow.FollowRepository;
import com.instaclone.social.like.LikeRepository;
import com.instaclone.social.like.LikeableType;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationService {

    private final UserModerationRepository moderationRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final FollowRequestNotificationCleaner followRequestNotifications;

    public ModerationService(
            UserModerationRepository moderationRepository,
            FollowRepository followRepository,
            UserRepository userRepository,
            PostRepository postRepository,
            LikeRepository likeRepository,
            CommentRepository commentRepository,
            FollowRequestNotificationCleaner followRequestNotifications) {
        this.moderationRepository = moderationRepository;
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.followRequestNotifications = followRequestNotifications;
    }

    @Transactional
    public void block(Long actorId, String targetUsername) {
        User target = userRepository.findByUsername(targetUsername).orElseThrow(() -> new NotFoundException("User not found"));
        if (target.getId().equals(actorId)) {
            throw new BadRequestException("You cannot block yourself");
        }
        if (moderationRepository.existsByActorIdAndTargetIdAndType(actorId, target.getId(), ModerationType.BLOCK)) {
            return;
        }
        User actor = userRepository.getReferenceById(actorId);

        UserModeration block = new UserModeration();
        block.setActor(actor);
        block.setTarget(target);
        block.setType(ModerationType.BLOCK);
        block.setCreatedAt(Instant.now());
        moderationRepository.save(block);

        // A block supersedes a restrict of the same pair — drop a stale restrict row either
        // direction so it doesn't linger once the stronger relationship exists.
        moderationRepository
                .findByActorIdAndTargetIdAndType(actorId, target.getId(), ModerationType.RESTRICT)
                .ifPresent(moderationRepository::delete);

        // Blocking severs the follow relationship both directions, mirroring real Instagram —
        // a block that left an existing follow in place would still leak the blocked party's
        // posts into the blocker's home feed (FeedService.getHomeFeed is purely follow-based).
        followRepository.findByFollowerIdAndFolloweeId(actorId, target.getId()).ifPresent(followRepository::delete);
        followRepository.findByFollowerIdAndFolloweeId(target.getId(), actorId).ifPresent(followRepository::delete);
        // A pending request deleted above must not leave a "requested to follow you" notification
        // behind — after a block it would keep surfacing the blocked account.
        followRequestNotifications.clear(actorId, target.getId());
        followRequestNotifications.clear(target.getId(), actorId);

        // A block should retroactively clear pre-existing interactions too, not just gate future
        // ones — otherwise a like/comment made before the block stays visible to everyone forever.
        clearInteractions(actorId, target.getId());
        clearInteractions(target.getId(), actorId);
    }

    /** Removes every like/comment actorId left on postOwnerId's posts (and decrements the
     * affected posts' denormalized counters to match), as part of severing a block both ways. */
    private void clearInteractions(Long actorId, Long postOwnerId) {
        List<Long> postIds = postRepository.findIdsByUserId(postOwnerId);
        if (postIds.isEmpty()) {
            return;
        }

        for (Long postId : likeRepository.findLikedIds(actorId, LikeableType.POST, postIds)) {
            likeRepository.deleteByUserIdAndLikeableTypeAndLikeableId(actorId, LikeableType.POST, postId);
            postRepository.decrementLikeCount(postId);
        }

        List<Comment> comments = commentRepository.findByUserIdAndPostIdIn(actorId, postIds);
        Set<Long> topLevelIds =
                comments.stream().filter(c -> c.getParent() == null).map(Comment::getId).collect(Collectors.toSet());
        for (Comment comment : comments) {
            if (comment.getParent() != null && topLevelIds.contains(comment.getParent().getId())) {
                // Its parent is also being removed below, which cascades this reply away already.
                continue;
            }
            List<Long> replyIds =
                    comment.getParent() == null ? commentRepository.findReplyIdsByParentId(comment.getId()) : List.of();
            List<Long> removedIds = new ArrayList<>(replyIds);
            removedIds.add(comment.getId());
            likeRepository.deleteByLikeableTypeAndLikeableIdIn(LikeableType.COMMENT, removedIds);
            commentRepository.delete(comment);
            postRepository.decrementCommentCountBy(comment.getPost().getId(), removedIds.size());
        }
    }

    @Transactional
    public void unblock(Long actorId, String targetUsername) {
        User target = userRepository.findByUsername(targetUsername).orElseThrow(() -> new NotFoundException("User not found"));
        moderationRepository
                .findByActorIdAndTargetIdAndType(actorId, target.getId(), ModerationType.BLOCK)
                .ifPresent(moderationRepository::delete);
    }

    @Transactional
    public void restrict(Long actorId, String targetUsername) {
        User target = userRepository.findByUsername(targetUsername).orElseThrow(() -> new NotFoundException("User not found"));
        if (target.getId().equals(actorId)) {
            throw new BadRequestException("You cannot restrict yourself");
        }
        if (moderationRepository.existsByActorIdAndTargetIdAndType(actorId, target.getId(), ModerationType.RESTRICT)) {
            return;
        }
        User actor = userRepository.getReferenceById(actorId);

        UserModeration restrict = new UserModeration();
        restrict.setActor(actor);
        restrict.setTarget(target);
        restrict.setType(ModerationType.RESTRICT);
        restrict.setCreatedAt(Instant.now());
        moderationRepository.save(restrict);
    }

    @Transactional
    public void unrestrict(Long actorId, String targetUsername) {
        User target = userRepository.findByUsername(targetUsername).orElseThrow(() -> new NotFoundException("User not found"));
        moderationRepository
                .findByActorIdAndTargetIdAndType(actorId, target.getId(), ModerationType.RESTRICT)
                .ifPresent(moderationRepository::delete);
    }

    @Transactional(readOnly = true)
    public boolean isBlockedEitherDirection(Long aId, Long bId) {
        return moderationRepository.existsBlockEitherDirection(aId, bId);
    }

    /** Directional: has targetId blocked viewerId? Deliberately NOT "either direction" — a user
     * who blocks someone must retain the ability to view that person's profile (to reach
     * Unblock), while the person they blocked must not be able to view theirs at all. */
    @Transactional(readOnly = true)
    public boolean isBlockedBy(Long targetId, Long viewerId) {
        return moderationRepository.existsByActorIdAndTargetIdAndType(targetId, viewerId, ModerationType.BLOCK);
    }

    @Transactional(readOnly = true)
    public List<Long> getBlockedEitherDirectionIds(Long userId) {
        return moderationRepository.findBlockedEitherDirectionIds(userId);
    }

    @Transactional(readOnly = true)
    public boolean isRestrictedBy(Long restricterId, Long targetId) {
        return moderationRepository.existsByActorIdAndTargetIdAndType(restricterId, targetId, ModerationType.RESTRICT);
    }
}
