package io.smartip.interventions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = InterventionController.class)
class InterventionExceptionsHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    ProblemDetail handleValidation(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(InterventionNotFoundException.class)
    ProblemDetail handleNotFound(InterventionNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({
        InterventionReferenceAlreadyExistsException.class,
        NoAvailableTechnicianException.class,
        InvalidInterventionStatusTransitionException.class,
        TechnicianAssignmentRequiredException.class
    })
    ProblemDetail handleConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(TechnicianNotFoundException.class)
    ProblemDetail handleTechnicianNotFound(TechnicianNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InterventionAccessDeniedException.class)
    ProblemDetail handleForbidden(InterventionAccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }
}
