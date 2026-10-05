package com.instaclone.report;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

    @Query(
            "select r.targetId from Report r where r.reporter.id = :reporterId and r.targetType = "
                    + "com.instaclone.report.ReportTargetType.POST")
    List<Long> findReportedPostIds(@Param("reporterId") Long reporterId);

    /** A USER-type report has no post id of its own to filter on — this resolves it to the post ids
     * authored by that reported user, so reporting an account hides all of their content too, not
     * just a single reported post. */
    @Query(
            "select p.id from Post p where p.user.id in ("
                    + "select r.targetId from Report r where r.reporter.id = :reporterId and r.targetType = "
                    + "com.instaclone.report.ReportTargetType.USER)")
    List<Long> findPostIdsByReportedAuthors(@Param("reporterId") Long reporterId);
}
