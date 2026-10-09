package com.instaclone.social.comment.service;

import com.instaclone.common.exception.BadRequestException;
import com.instaclone.common.exception.ForbiddenException;
import com.instaclone.common.exception.NotFoundException;
import com.instaclone.common.pagination.Cursor;
import com.instaclone.common.pagination.CursorPage;
import com.instaclone.notification.enums.NotificationType;
import com.instaclone.notification.event.NotificationEvent;
import com.instaclone.post.entity.Post;
import com.instaclone.post.repository.PostRepository;
import com.instaclone.social.comment.dto.CommentResponse;
import com.instaclone.social.comment.dto.CreateCommentRequest;
import com.instaclone.social.comment.entity.Comment;
import com.instaclone.social.comment.repository.CommentRepository;
import com.instaclone.social.like.enums.LikeableType;
import com.instaclone.social.like.repository.LikeRepository;
import com.instaclone.user.dto.UserSummary;
import com.instaclone.user.entity.User;
import com.instaclone.user.repository.UserRepository;
import com.instaclone.social.mention.service.MentionService;
import com.instaclone.user.service.ProfileVisibilityService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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
    private final ApplicationEventPublisher eventPublisher;
    private final MentionService mentionService;

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            LikeRepository likeRepository,
            ProfileVisibilityService profileVisibilityService,
            ApplicationEventPublisher eventPublisher,
            MentionService mentionService) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.likeRepository = likeRepository;
        this.profileVisibilityService = profileVisibilityService;
        this.eventPublisher = eventPublisher;
        this.mentionService = mentionService;
    }

    @Transactional
    public CommentResponse addComment(Long postId, Long userId, CreateCommentRequest request) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        User author = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        assertVisible(post, author);

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

        // Mentions skip anyone already notified of this comment above (post owner, replied-to author).
        Set<Long> alreadyNotified = new HashSet<>();
        alreadyNotified.add(postOwnerId);
        if (parent != null) {
            alreadyNotified.add(parent.getUser().getId());
        }
        mentionService.notifyCommentMentions(author, post, request.text(), alreadyNotified);

        // A freshly created comment can never already be liked by its own author.
        return toResponse(comment, UserSummary.from(author), false);
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

        // Mirrors PostService.enrich: one batched lookup of the viewer's liked ids among this
        // page's comments, rather than an existence check per comment. Guard the empty-page case
        // explicitly rather than relying on Hibernate's handling of an empty "in" list.
        List<Long> commentIds = page.items().stream().map(Comment::getId).toList();
        Set<Long> likedCommentIds = commentIds.isEmpty()
                ? Set.of()
                : new HashSet<>(likeRepository.findLikedIds(viewerId, LikeableType.COMMENT, commentIds));

        List<CommentResponse> items = page.items().stream()
                .map(c -> toResponse(c, authorsById.get(c.getUser().getId()), likedCommentIds.contains(c.getId())))
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

    private CommentResponse toResponse(Comment comment, UserSummary author, boolean likedByViewer) {
        return new CommentResponse(
                comment.getId(),
                author,
                comment.getText(),
                comment.getParent() != null ? comment.getParent().getId() : null,
                comment.getLikeCount(),
                likedByViewer,
                comment.getCreatedAt());
    }
}
