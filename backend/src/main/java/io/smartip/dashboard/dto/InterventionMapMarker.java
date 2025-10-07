package io.smartip.dashboard.dto;

import java.time.Instant;

public record InterventionMapMarker(
        long interventionId,
        double latitude,
        double longitude,
        String status,
        Long technicianId,
        Instant plannedAt,
        Instant updatedAt) {}
