package com.instaclone.report.service;

import com.instaclone.common.exception.BadRequestException;
import com.instaclone.common.exception.NotFoundException;
import com.instaclone.post.entity.Post;
import com.instaclone.post.repository.PostRepository;
import com.instaclone.report.dto.CreateReportRequest;
import com.instaclone.report.dto.ReportResponse;
import com.instaclone.report.entity.Report;
import com.instaclone.report.enums.ReportTargetType;
import com.instaclone.report.repository.ReportRepository;
import com.instaclone.social.block.repository.UserBlockRepository;
import com.instaclone.social.comment.entity.Comment;
import com.instaclone.social.comment.repository.CommentRepository;
import com.instaclone.user.entity.User;
import com.instaclone.user.repository.UserRepository;
import com.instaclone.user.service.ProfileVisibilityService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records user reports against posts, comments and accounts. Reports are stored for later review
 * and never change what anyone sees on their own — no auto-hiding, since that would hand anyone a
 * way to silence a post by reporting it.
 *
 * <p>A reporter can only report what they could see: a post or comment they have no access to
 * (private account, or blocked) is reported as not-found, the same answer the content endpoints
 * give. Reporting the same target twice is idempotent and returns the original report.
 */
@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ProfileVisibilityService profileVisibilityService;
    private final UserBlockRepository blockRepository;

    public ReportService(
            ReportRepository reportRepository,
            PostRepository postRepository,
            CommentRepository commentRepository,
            UserRepository userRepository,
            ProfileVisibilityService profileVisibilityService,
            UserBlockRepository blockRepository) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.profileVisibilityService = profileVisibilityService;
        this.blockRepository = blockRepository;
    }

    @Transactional
    public ReportResponse reportPost(Long reporterId, Long postId, CreateReportRequest request) {
        User reporter = loadUser(reporterId);
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        assertReportable(post.getUser(), reporter, "post");
        return save(reporter, ReportTargetType.POST, postId, request);
    }

    @Transactional
    public ReportResponse reportComment(Long reporterId, Long commentId, CreateReportRequest request) {
        User reporter = loadUser(reporterId);
        Comment comment =
                commentRepository.findById(commentId).orElseThrow(() -> new NotFoundException("Comment not found"));
        // Visibility follows the post the comment lives on; the "own content" rule follows the commenter.
        if (!profileVisibilityService.isVisible(comment.getPost().getUser(), reporter)
                || !comment.isVisibleTo(reporterId)) {
            throw new NotFoundException("Comment not found");
        }
        if (comment.getUser().getId().equals(reporterId)) {
            throw new BadRequestException("You cannot report your own comment");
        }
        return save(reporter, ReportTargetType.COMMENT, commentId, request);
    }

    @Transactional
    public ReportResponse reportUser(Long reporterId, String username, CreateReportRequest request) {
        User reporter = loadUser(reporterId);
        User target = userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
        if (target.getId().equals(reporterId)) {
            throw new BadRequestException("You cannot report yourself");
        }
        // Someone who blocked the reporter shouldn't be discoverable by reporting them. A reporter
        // who blocked the target may still report them — that's the normal block-then-report flow.
        if (blockRepository.existsByBlockerIdAndBlockedId(target.getId(), reporterId)) {
            throw new NotFoundException("User not found");
        }
        return save(reporter, ReportTargetType.USER, target.getId(), request);
    }

    private void assertReportable(User author, User reporter, String what) {
        if (!profileVisibilityService.isVisible(author, reporter)) {
            throw new NotFoundException(what + " not found");
        }
        if (author.getId().equals(reporter.getId())) {
            throw new BadRequestException("You cannot report your own " + what);
        }
    }

    private ReportResponse save(User reporter, ReportTargetType type, Long targetId, CreateReportRequest request) {
        Report report = reportRepository
                .findByReporterIdAndTargetTypeAndTargetId(reporter.getId(), type, targetId)
                .orElseGet(() -> {
                    Report fresh = new Report();
                    fresh.setReporter(reporter);
                    fresh.setTargetType(type);
                    fresh.setTargetId(targetId);
                    fresh.setReason(request.reason());
                    fresh.setDetails(request.details() == null || request.details().isBlank() ? null : request.details().trim());
                    fresh.setCreatedAt(Instant.now());
                    return reportRepository.save(fresh);
                });
        return new ReportResponse(report.getId(), report.getStatus());
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
