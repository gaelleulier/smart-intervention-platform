package io.smartip.interventions.dto;

import io.smartip.domain.InterventionAssignmentMode;
import io.smartip.interventions.InterventionService.CreateInterventionCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateInterventionRequest(
        @NotBlank @Size(max = 50) String reference,
        @NotBlank @Size(max = 160) String title,
        @Size(max = 4000) String description,
        @NotNull Instant plannedAt,
        @NotNull InterventionAssignmentMode assignmentMode,
        Long technicianId,
        @DecimalMin(value = "-90.0", inclusive = true, message = "Latitude must be >= -90")
                @DecimalMax(value = "90.0", inclusive = true, message = "Latitude must be <= 90")
                Double latitude,
        @DecimalMin(value = "-180.0", inclusive = true, message = "Longitude must be >= -180")
                @DecimalMax(value = "180.0", inclusive = true, message = "Longitude must be <= 180")
                Double longitude) {

    public CreateInterventionCommand toCommand() {
        return new CreateInterventionCommand(
                reference, title, description, plannedAt, assignmentMode, technicianId, latitude, longitude);
    }
}
