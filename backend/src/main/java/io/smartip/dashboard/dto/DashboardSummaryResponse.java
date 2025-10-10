package io.smartip.dashboard.dto;

import java.time.Instant;

public record DashboardSummaryResponse(
        long totalInterventions,
        long scheduledCount,
        long inProgressCount,
        long completedCount,
        long validatedCount,
        Double averageCompletionSeconds,
        Double validationRatio,
        Instant lastRefreshedAt) {}
