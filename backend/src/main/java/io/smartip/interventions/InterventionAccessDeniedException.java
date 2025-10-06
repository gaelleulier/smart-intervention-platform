package io.smartip.interventions;

class InterventionAccessDeniedException extends RuntimeException {

    InterventionAccessDeniedException(Long interventionId) {
        super("Access denied for intervention " + interventionId);
    }
}
