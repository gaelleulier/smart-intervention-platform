package io.smartip.interventions.dto;

import io.smartip.domain.InterventionAssignmentMode;
import io.smartip.interventions.InterventionService.UpdateInterventionCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateInterventionRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 4000) String description,
        @NotNull Instant plannedAt,
        @NotNull InterventionAssignmentMode assignmentMode,
        Long technicianId,
        @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
                @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
                Double latitude,
        @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
                @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
                Double longitude) {

    public UpdateInterventionCommand toCommand() {
        return new UpdateInterventionCommand(
                title, description, plannedAt, assignmentMode, technicianId, latitude, longitude);
    }
}
