package com.instaclone.report;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(@NotNull ReportTargetType targetType, @NotNull Long targetId, @Size(max = 255) String reason) {}
