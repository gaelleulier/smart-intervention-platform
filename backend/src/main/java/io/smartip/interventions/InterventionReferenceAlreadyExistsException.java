package io.smartip.interventions;

class InterventionReferenceAlreadyExistsException extends RuntimeException {

    InterventionReferenceAlreadyExistsException(String reference) {
        super("Intervention reference already exists: " + reference);
    }
}
