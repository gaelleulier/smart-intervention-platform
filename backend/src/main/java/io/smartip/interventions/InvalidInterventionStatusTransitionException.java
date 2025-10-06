package io.smartip.interventions;

import io.smartip.domain.InterventionStatus;

class InvalidInterventionStatusTransitionException extends RuntimeException {

    InvalidInterventionStatusTransitionException(InterventionStatus current, InterventionStatus next) {
        super("Cannot transition intervention from " + current + " to " + next);
    }
}
