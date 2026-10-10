package com.instaclone.social.like.service;

import com.instaclone.common.exception.ForbiddenException;
import com.instaclone.common.exception.NotFoundException;
import com.instaclone.notification.enums.NotificationType;
import com.instaclone.notification.event.NotificationEvent;
import com.instaclone.post.entity.Post;
import com.instaclone.post.repository.PostRepository;
import com.instaclone.social.comment.entity.Comment;
import com.instaclone.social.comment.repository.CommentRepository;
import com.instaclone.social.like.dto.LikeCountResponse;
import com.instaclone.social.like.entity.Like;
import com.instaclone.social.like.enums.LikeableType;
import com.instaclone.social.like.repository.LikeRepository;
import com.instaclone.user.entity.User;
import com.instaclone.user.repository.UserRepository;
import com.instaclone.user.service.ProfileVisibilityService;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LikeService {

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ProfileVisibilityService profileVisibilityService;
    private final ApplicationEventPublisher eventPublisher;

    public LikeService(
            LikeRepository likeRepository,
            PostRepository postRepository,
            CommentRepository commentRepository,
            UserRepository userRepository,
            ProfileVisibilityService profileVisibilityService,
            ApplicationEventPublisher eventPublisher) {
        this.likeRepository = likeRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.profileVisibilityService = profileVisibilityService;
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

    @Transactional
    public LikeCountResponse likeComment(Long userId, Long commentId) {
        Comment comment = commentRepository
                .findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));
        User viewer = userRepository.getReferenceById(userId);
        assertVisible(comment.getPost(), viewer);
        if (!comment.isVisibleTo(userId)) {
            throw new NotFoundException("Comment not found"); // restricted user's comment awaiting approval
        }

        boolean alreadyLiked =
                likeRepository.existsByUserIdAndLikeableTypeAndLikeableId(userId, LikeableType.COMMENT, commentId);
        if (!alreadyLiked) {
            Like like = new Like();
            like.setUser(viewer);
            like.setLikeableType(LikeableType.COMMENT);
            like.setLikeableId(commentId);
            like.setCreatedAt(Instant.now());
            likeRepository.save(like);

            commentRepository.incrementLikeCount(commentId);

            Long recipientId = comment.getUser().getId();
            if (!recipientId.equals(userId)) {
                // No standalone comment detail page to link to, so this reuses the same
                // targetType="POST" convention CommentService already uses for comment/reply
                // notifications, pointing at the post the comment lives on.
                eventPublisher.publishEvent(new NotificationEvent(
                        recipientId, userId, NotificationType.LIKE, "POST", comment.getPost().getId()));
            }
        }

        long likeCount = alreadyLiked ? comment.getLikeCount() : comment.getLikeCount() + 1;
        return new LikeCountResponse(likeCount, true);
    }

    @Transactional
    public LikeCountResponse unlikeComment(Long userId, Long commentId) {
        Comment comment = commentRepository
                .findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));
        User viewer = userRepository.getReferenceById(userId);
        assertVisible(comment.getPost(), viewer);
        if (!comment.isVisibleTo(userId)) {
            throw new NotFoundException("Comment not found"); // restricted user's comment awaiting approval
        }

        boolean removed = likeRepository
                .findByUserIdAndLikeableTypeAndLikeableId(userId, LikeableType.COMMENT, commentId)
                .map(like -> {
                    likeRepository.delete(like);
                    return true;
                })
                .orElse(false);
        if (removed) {
            commentRepository.decrementLikeCount(commentId);
        }

        long likeCount = removed ? Math.max(0, comment.getLikeCount() - 1) : comment.getLikeCount();
        return new LikeCountResponse(likeCount, false);
    }

    private void assertVisible(Post post, User viewer) {
        if (!profileVisibilityService.isVisible(post.getUser(), viewer)) {
            throw new ForbiddenException("This account is private");
        }
    }
}
