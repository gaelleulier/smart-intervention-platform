package io.smartip.interventions;

class TechnicianNotFoundException extends RuntimeException {

    TechnicianNotFoundException(Long id) {
        super("Technician " + id + " not found");
    }
}
