package io.smartip.interventions.dto;

import java.time.Instant;
import java.util.List;

public record SmartAssignmentResponse(
        SmartAssignmentCandidate recommended,
        List<SmartAssignmentCandidate> alternatives,
        String rationale,
        Instant generatedAt) {}
