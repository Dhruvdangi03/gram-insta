package com.instaclone.report;

import com.instaclone.common.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@Valid @RequestBody CreateReportRequest request, @AuthenticationPrincipal Jwt jwt) {
        reportService.report(SecurityUtils.currentUserId(jwt), request);
    }
}
