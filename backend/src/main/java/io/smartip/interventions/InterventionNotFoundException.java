package io.smartip.interventions;

class InterventionNotFoundException extends RuntimeException {

    InterventionNotFoundException(Long id) {
        super("Intervention " + id + " not found");
    }
}
