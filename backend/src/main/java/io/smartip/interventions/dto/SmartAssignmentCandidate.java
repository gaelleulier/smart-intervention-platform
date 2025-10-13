package io.smartip.interventions.dto;

public record SmartAssignmentCandidate(
        long technicianId,
        String fullName,
        String email,
        double overallScore,
        double workloadScore,
        double distanceScore,
        double skillScore,
        Double distanceKm,
        long openAssignments,
        long matchingHistory) {}
