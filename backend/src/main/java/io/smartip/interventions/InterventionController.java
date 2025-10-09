package io.smartip.interventions;

import io.smartip.domain.InterventionAssignmentMode;
import io.smartip.domain.InterventionStatus;
import io.smartip.interventions.dto.CreateInterventionRequest;
import io.smartip.interventions.dto.InterventionPageResponse;
import io.smartip.interventions.dto.InterventionResponse;
import io.smartip.interventions.dto.UpdateInterventionRequest;
import io.smartip.interventions.dto.UpdateInterventionStatusRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interventions")
public class InterventionController {

    private final InterventionService interventionService;

    public InterventionController(InterventionService interventionService) {
        this.interventionService = interventionService;
    }

    @GetMapping
    public InterventionPageResponse list(
            @PageableDefault(size = 20, sort = "plannedAt", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "status", required = false) InterventionStatus status,
            @RequestParam(value = "assignmentMode", required = false) InterventionAssignmentMode assignmentMode,
            @RequestParam(value = "technicianId", required = false) Long technicianId,
            @RequestParam(value = "plannedFrom", required = false) Instant plannedFrom,
            @RequestParam(value = "plannedTo", required = false) Instant plannedTo) {
        String sanitizedQuery = query != null ? query.trim() : null;
        var filters = new InterventionService.InterventionFilters(
                sanitizedQuery, status, assignmentMode, technicianId, plannedFrom, plannedTo);
        Page<InterventionResponse> page = interventionService
                .findAll(filters, pageable)
                .map(InterventionResponse::fromEntity);
        return InterventionPageResponse.fromPage(page);
    }

    @GetMapping("/{id}")
    public InterventionResponse get(@PathVariable Long id) {
        return InterventionResponse.fromEntity(interventionService.getIntervention(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterventionResponse create(@Valid @RequestBody CreateInterventionRequest request) {
        return InterventionResponse.fromEntity(interventionService.createIntervention(request.toCommand()));
    }

    @PutMapping("/{id}")
    public InterventionResponse update(@PathVariable Long id, @Valid @RequestBody UpdateInterventionRequest request) {
        return InterventionResponse.fromEntity(interventionService.updateIntervention(id, request.toCommand()));
    }

    @PostMapping("/{id}/status")
    public InterventionResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateInterventionStatusRequest request,
            Authentication authentication) {
        if (hasRole(authentication, "ROLE_TECH")) {
            var intervention = interventionService.getIntervention(id);
            if (intervention.getTechnician() == null
                    || !intervention.getTechnician()
                            .getEmail()
                            .equalsIgnoreCase(authentication.getName())) {
                throw new InterventionAccessDeniedException(id);
            }
            if (request.status() == InterventionStatus.VALIDATED) {
                throw new InterventionAccessDeniedException(id);
            }
        }
        return InterventionResponse.fromEntity(interventionService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        interventionService.deleteIntervention(id);
    }

    private boolean hasRole(Authentication authentication, String authority) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
