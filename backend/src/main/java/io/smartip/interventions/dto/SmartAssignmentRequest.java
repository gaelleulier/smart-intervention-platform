package io.smartip.interventions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SmartAssignmentRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 4000) String description,
        Double latitude,
        Double longitude,
        Long interventionId) {}
