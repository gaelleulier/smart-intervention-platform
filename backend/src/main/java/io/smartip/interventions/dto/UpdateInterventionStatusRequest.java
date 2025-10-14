package io.smartip.interventions.dto;

import io.smartip.domain.InterventionStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateInterventionStatusRequest(@NotNull InterventionStatus status) {}
