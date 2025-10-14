package io.smartip.interventions;

class NoAvailableTechnicianException extends RuntimeException {

    NoAvailableTechnicianException() {
        super("No technician available for automatic assignment");
    }
}
