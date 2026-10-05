package com.instaclone.report;

import com.instaclone.common.BadRequestException;
import com.instaclone.common.NotFoundException;
import com.instaclone.post.PostRepository;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public ReportService(ReportRepository reportRepository, PostRepository postRepository, UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void report(Long reporterId, CreateReportRequest request) {
        if (request.targetType() == ReportTargetType.POST) {
            if (!postRepository.existsById(request.targetId())) {
                throw new NotFoundException("Post not found");
            }
        } else {
            User target = userRepository.findById(request.targetId()).orElseThrow(() -> new NotFoundException("User not found"));
            if (target.getId().equals(reporterId)) {
                throw new BadRequestException("You cannot report yourself");
            }
        }

        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, request.targetType(), request.targetId())) {
            return;
        }

        User reporter = userRepository.getReferenceById(reporterId);
        Report report = new Report();
        report.setReporter(reporter);
        report.setTargetType(request.targetType());
        report.setTargetId(request.targetId());
        report.setReason(request.reason());
        report.setCreatedAt(Instant.now());
        reportRepository.save(report);
    }
}
