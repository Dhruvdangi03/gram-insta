package com.instaclone.social.comment;

import com.instaclone.common.BadRequestException;
import com.instaclone.common.Cursor;
import com.instaclone.common.CursorPage;
import com.instaclone.common.ForbiddenException;
import com.instaclone.common.NotFoundException;
import com.instaclone.notification.NotificationEvent;
import com.instaclone.notification.NotificationType;
import com.instaclone.post.Post;
import com.instaclone.post.PostRepository;
import com.instaclone.social.like.LikeRepository;
import com.instaclone.social.like.LikeableType;
import com.instaclone.social.moderation.ModerationService;
import com.instaclone.user.ProfileVisibilityService;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import com.instaclone.user.UserSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final ProfileVisibilityService profileVisibilityService;
    private final ModerationService moderationService;
    private final ApplicationEventPublisher eventPublisher;

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            LikeRepository likeRepository,
            ProfileVisibilityService profileVisibilityService,
            ModerationService moderationService,
            ApplicationEventPublisher eventPublisher) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.likeRepository = likeRepository;
        this.profileVisibilityService = profileVisibilityService;
        this.moderationService = moderationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CommentResponse addComment(Long postId, Long userId, CreateCommentRequest request) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        User author = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        assertVisible(post, author);
        // assertVisible is deliberately directional (lets a blocker still view a blocked account's
        // posts to reach Unblock) — but commenting is a write, so it must also check the
        // commenter's own side of the block, which assertVisible alone doesn't cover.
        if (moderationService.isBlockedEitherDirection(userId, post.getUser().getId())) {
            throw new ForbiddenException("You can't interact with this account");
        }

        Comment parent = null;
        if (request.parentCommentId() != null) {
            parent = commentRepository
                    .findById(request.parentCommentId())
                    .orElseThrow(() -> new NotFoundException("Parent comment not found"));
            if (!parent.getPost().getId().equals(postId)) {
                throw new BadRequestException("Parent comment does not belong to this post");
            }
            if (parent.isReply()) {
                throw new BadRequestException("Replies can only be one level deep");
            }
        }

        Comment comment = new Comment();
        comment.setPost(post);
        comment.setUser(author);
        comment.setParent(parent);
        comment.setText(request.text());
        comment.setCreatedAt(Instant.now());
        comment = commentRepository.save(comment);

        postRepository.incrementCommentCount(postId);

        Long postOwnerId = post.getUser().getId();
        if (!postOwnerId.equals(userId)) {
            eventPublisher.publishEvent(new NotificationEvent(postOwnerId, userId, NotificationType.COMMENT, "POST", postId));
        }
        // A reply should also notify the comment it's replying to, not just the post owner — the
        // two can easily be different people, and the reply is directed at the parent's author.
        if (parent != null) {
            Long parentAuthorId = parent.getUser().getId();
            if (!parentAuthorId.equals(userId) && !parentAuthorId.equals(postOwnerId)) {
                eventPublisher.publishEvent(
                        new NotificationEvent(parentAuthorId, userId, NotificationType.COMMENT, "POST", postId));
            }
        }

        return toResponse(comment, UserSummary.from(author));
    }

    @Transactional(readOnly = true)
    public CursorPage<CommentResponse> getComments(Long postId, Long viewerId, String cursor, int limit) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        User viewer = userRepository.findById(viewerId).orElseThrow(() -> new NotFoundException("User not found"));
        assertVisible(post, viewer);

        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<Comment> rows = decoded == null
                ? commentRepository.findFirstPageByPostId(postId, limit + 1)
                : commentRepository.findPageByPostIdAfterCursor(postId, decoded.createdAt(), decoded.id(), limit + 1);

        CursorPage<Comment> page = CursorPage.of(rows, limit, c -> new Cursor(c.getCreatedAt(), c.getId()).encode());

        Set<Long> authorIds = page.items().stream().map(c -> c.getUser().getId()).collect(Collectors.toSet());
        Map<Long, UserSummary> authorsById = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, UserSummary::from));

        Long postOwnerId = post.getUser().getId();
        List<CommentResponse> items = page.items().stream()
                // A comment from someone the post's owner has restricted is hidden from every
                // viewer except the restricted author themselves and the post owner reviewing it —
                // the author never finds out, matching real Instagram's silent restrict behavior.
                .filter(c -> {
                    Long authorId = c.getUser().getId();
                    if (viewerId.equals(authorId) || viewerId.equals(postOwnerId)) {
                        return true;
                    }
                    return !moderationService.isRestrictedBy(postOwnerId, authorId);
                })
                .map(c -> toResponse(c, authorsById.get(c.getUser().getId())))
                .toList();
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }

    @Transactional
    public void deleteComment(Long commentId, Long requesterId) {
        Comment comment =
                commentRepository.findById(commentId).orElseThrow(() -> new NotFoundException("Comment not found"));
        if (!comment.getUser().getId().equals(requesterId)) {
            throw new ForbiddenException("You can only delete your own comments");
        }

        // parent_comment_id has ON DELETE CASCADE, so deleting a top-level comment silently
        // deletes its replies too — account for those rows explicitly, both for the post's
        // comment_count and for cleaning up their now-orphaned likes.
        List<Long> replyIds = commentRepository.findReplyIdsByParentId(commentId);
        List<Long> deletedCommentIds = new ArrayList<>(replyIds);
        deletedCommentIds.add(commentId);
        likeRepository.deleteByLikeableTypeAndLikeableIdIn(LikeableType.COMMENT, deletedCommentIds);

        Long postId = comment.getPost().getId();
        commentRepository.delete(comment);
        postRepository.decrementCommentCountBy(postId, 1 + replyIds.size());
    }

    private void assertVisible(Post post, User viewer) {
        if (!profileVisibilityService.isVisible(post.getUser(), viewer)) {
            throw new ForbiddenException("This account is private");
        }
    }

    private CommentResponse toResponse(Comment comment, UserSummary author) {
        return new CommentResponse(
                comment.getId(),
                author,
                comment.getText(),
                comment.getParent() != null ? comment.getParent().getId() : null,
                comment.getLikeCount(),
                comment.getCreatedAt());
    }
}
