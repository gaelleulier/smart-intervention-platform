package io.smartip.interventions.dto;

import io.smartip.domain.InterventionAssignmentMode;
import io.smartip.domain.InterventionEntity;
import io.smartip.domain.InterventionStatus;
import io.smartip.domain.UserEntity;
import java.time.Instant;

public record InterventionResponse(
        Long id,
        String reference,
        String title,
        String description,
        InterventionStatus status,
        InterventionAssignmentMode assignmentMode,
        Instant plannedAt,
        Instant startedAt,
        Instant completedAt,
        Instant validatedAt,
        Instant createdAt,
        Instant updatedAt,
        TechnicianSummary technician) {

    public static InterventionResponse fromEntity(InterventionEntity entity) {
        UserEntity technician = entity.getTechnician();
        TechnicianSummary technicianSummary = technician != null
                ? new TechnicianSummary(technician.getId(), technician.getFullName(), technician.getEmail())
                : null;
        return new InterventionResponse(
                entity.getId(),
                entity.getReference(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getAssignmentMode(),
                entity.getPlannedAt(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getValidatedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                technicianSummary);
    }

    public record TechnicianSummary(Long id, String fullName, String email) {}
}
