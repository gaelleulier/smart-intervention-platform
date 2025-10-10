package io.smartip.dashboard.dto;

public record TechnicianLoadResponse(
        long technicianId,
        String technicianName,
        String technicianEmail,
        long openCount,
        long completedToday,
        Double averageCompletionSeconds,
        String lastRefreshedAt) {}
