package io.smartip.dashboard;

import java.time.Instant;

public record TechnicianLoadSnapshot(
        long technicianId,
        String fullName,
        String email,
        long openCount,
        long completedToday,
        Double averageCompletionSeconds,
        Instant lastRefreshedAt) {}
