package io.smartip.interventions;

class TechnicianAssignmentRequiredException extends RuntimeException {

    TechnicianAssignmentRequiredException(Long interventionId) {
        super("Technician assignment required before starting intervention " + interventionId);
    }
}
