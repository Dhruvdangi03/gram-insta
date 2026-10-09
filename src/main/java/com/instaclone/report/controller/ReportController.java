package com.instaclone.report.controller;

import com.instaclone.common.util.SecurityUtils;
import com.instaclone.report.dto.CreateReportRequest;
import com.instaclone.report.dto.ReportResponse;
import com.instaclone.report.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/posts/{id}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse reportPost(
            @PathVariable Long id, @Valid @RequestBody CreateReportRequest request, @AuthenticationPrincipal Jwt jwt) {
        return reportService.reportPost(SecurityUtils.currentUserId(jwt), id, request);
    }

    @PostMapping("/comments/{id}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse reportComment(
            @PathVariable Long id, @Valid @RequestBody CreateReportRequest request, @AuthenticationPrincipal Jwt jwt) {
        return reportService.reportComment(SecurityUtils.currentUserId(jwt), id, request);
    }

    @PostMapping("/users/{username}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse reportUser(
            @PathVariable String username,
            @Valid @RequestBody CreateReportRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return reportService.reportUser(SecurityUtils.currentUserId(jwt), username, request);
    }
}
