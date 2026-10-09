package com.instaclone.report.repository;

import com.instaclone.report.entity.Report;
import com.instaclone.report.enums.ReportTargetType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);
}
