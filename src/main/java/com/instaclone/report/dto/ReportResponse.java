package com.instaclone.report.dto;

import com.instaclone.report.enums.ReportStatus;

public record ReportResponse(Long id, ReportStatus status) {}
