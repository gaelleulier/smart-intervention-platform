package io.smartip.interventions.dto;

import io.smartip.domain.InterventionAssignmentMode;
import io.smartip.interventions.InterventionService.UpdateInterventionCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateInterventionRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 4000) String description,
        @NotNull Instant plannedAt,
        @NotNull InterventionAssignmentMode assignmentMode,
        Long technicianId) {

    public UpdateInterventionCommand toCommand() {
        return new UpdateInterventionCommand(title, description, plannedAt, assignmentMode, technicianId);
    }
}
