package com.instaclone.report.dto;

import com.instaclone.report.enums.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(@NotNull ReportReason reason, @Size(max = 500) String details) {}
