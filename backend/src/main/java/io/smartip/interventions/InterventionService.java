package io.smartip.interventions;

import io.smartip.domain.InterventionAssignmentMode;
import io.smartip.domain.InterventionEntity;
import io.smartip.domain.InterventionRepository;
import io.smartip.domain.InterventionStatus;
import io.smartip.domain.UserEntity;
import io.smartip.domain.UserRepository;
import io.smartip.domain.UserRole;
import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterventionService {

    private static final List<InterventionStatus> OPEN_STATUSES =
            List.of(InterventionStatus.SCHEDULED, InterventionStatus.IN_PROGRESS);

    private final InterventionRepository interventionRepository;
    private final UserRepository userRepository;

    public InterventionService(InterventionRepository interventionRepository, UserRepository userRepository) {
        this.interventionRepository = interventionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<InterventionEntity> findAll(InterventionFilters filters, Pageable pageable) {
        Specification<InterventionEntity> specification = Specification.where((root, query, builder) -> builder.conjunction());

        if (filters.query() != null && !filters.query().isBlank()) {
            String term = "%" + filters.query().trim().toLowerCase() + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("reference")), term),
                    builder.like(builder.lower(root.get("title")), term)));
        }

        if (filters.status() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), filters.status()));
        }

        if (filters.assignmentMode() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("assignmentMode"), filters.assignmentMode()));
        }

        if (filters.technicianId() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(
                    root.join("technician", JoinType.LEFT).get("id"), filters.technicianId()));
        }

        if (filters.plannedFrom() != null) {
            specification = specification.and((root, query, builder) -> builder.greaterThanOrEqualTo(
                    root.get("plannedAt"), filters.plannedFrom()));
        }

        if (filters.plannedTo() != null) {
            specification = specification.and(
                    (root, query, builder) -> builder.lessThanOrEqualTo(root.get("plannedAt"), filters.plannedTo()));
        }

        Page<InterventionEntity> page = interventionRepository.findAll(specification, pageable);
        page.getContent().forEach(this::initializeTechnician);
        return page;
    }

    @Transactional(readOnly = true)
    public InterventionEntity getIntervention(Long id) {
        return initializeTechnician(
                interventionRepository.findById(id).orElseThrow(() -> new InterventionNotFoundException(id)));
    }

    @Transactional
    public InterventionEntity createIntervention(CreateInterventionCommand command) {
        String reference = command.reference().trim();
        if (interventionRepository.existsByReferenceIgnoreCase(reference)) {
            throw new InterventionReferenceAlreadyExistsException(reference);
        }

        InterventionEntity entity = new InterventionEntity();
        entity.setReference(reference);
        entity.setTitle(command.title().trim());
        entity.setDescription(normalizeDescription(command.description()));
        entity.setPlannedAt(command.plannedAt());
        entity.setStatus(InterventionStatus.SCHEDULED);
        entity.setAssignmentMode(command.assignmentMode());
        entity.setLatitude(normalizeCoordinate(command.latitude()));
        entity.setLongitude(normalizeCoordinate(command.longitude()));
        applyAssignment(entity, command.assignmentMode(), command.technicianId(), command.plannedAt());
        return initializeTechnician(interventionRepository.save(entity));
    }

    @Transactional
    public InterventionEntity updateIntervention(Long id, UpdateInterventionCommand command) {
        InterventionEntity entity = interventionRepository
                .findById(id)
                .orElseThrow(() -> new InterventionNotFoundException(id));

        entity.setTitle(command.title().trim());
        entity.setDescription(normalizeDescription(command.description()));
        entity.setPlannedAt(command.plannedAt());
        entity.setAssignmentMode(command.assignmentMode());
        entity.setLatitude(normalizeCoordinate(command.latitude()));
        entity.setLongitude(normalizeCoordinate(command.longitude()));
        applyAssignment(entity, command.assignmentMode(), command.technicianId(), command.plannedAt());
        return initializeTechnician(interventionRepository.save(entity));
    }

    @Transactional
    public InterventionEntity updateStatus(Long id, InterventionStatus nextStatus) {
        InterventionEntity entity = interventionRepository
                .findById(id)
                .orElseThrow(() -> new InterventionNotFoundException(id));
        initializeTechnician(entity);

        InterventionStatus current = entity.getStatus();
        if (current == nextStatus) {
            return entity;
        }

        if (!isValidTransition(current, nextStatus)) {
            throw new InvalidInterventionStatusTransitionException(current, nextStatus);
        }

        if (nextStatus == InterventionStatus.IN_PROGRESS && entity.getTechnician() == null) {
            throw new TechnicianAssignmentRequiredException(id);
        }

        entity.setStatus(nextStatus);
        Instant now = Instant.now();
        switch (nextStatus) {
            case IN_PROGRESS -> {
                if (entity.getStartedAt() == null) {
                    entity.setStartedAt(now);
                }
            }
            case COMPLETED -> {
                if (entity.getCompletedAt() == null) {
                    entity.setCompletedAt(now);
                }
            }
            case VALIDATED -> {
                if (entity.getValidatedAt() == null) {
                    entity.setValidatedAt(now);
                }
            }
            default -> {
                // no-op
            }
        }
        return initializeTechnician(interventionRepository.save(entity));
    }

    private void applyAssignment(
            InterventionEntity entity,
            InterventionAssignmentMode mode,
            Long technicianId,
            Instant plannedAt) {
        if (mode == InterventionAssignmentMode.AUTO) {
            UserEntity technician = selectTechnicianForAutoAssignment(plannedAt);
            entity.setTechnician(technician);
            entity.setAssignmentMode(InterventionAssignmentMode.AUTO);
        } else {
            entity.setAssignmentMode(InterventionAssignmentMode.MANUAL);
            if (technicianId != null) {
                entity.setTechnician(resolveTechnician(technicianId));
            } else {
                entity.setTechnician(null);
            }
        }
    }

    private UserEntity selectTechnicianForAutoAssignment(Instant plannedAt) {
        List<UserEntity> technicians = userRepository.findByRoleOrderByIdAsc(UserRole.TECH);
        Comparator<UserEntity> comparator = Comparator.comparingLong((UserEntity tech) ->
                interventionRepository.countByTechnician_IdAndStatusIn(tech.getId(), OPEN_STATUSES));
        comparator = comparator.thenComparing(tech -> Optional.ofNullable(tech.getId()).orElse(Long.MAX_VALUE));
        return technicians.stream()
                .min(comparator)
                .orElseThrow(NoAvailableTechnicianException::new);
    }

    private UserEntity resolveTechnician(Long technicianId) {
        UserEntity technician = userRepository
                .findById(technicianId)
                .orElseThrow(() -> new TechnicianNotFoundException(technicianId));
        if (technician.getRole() != UserRole.TECH) {
            throw new TechnicianNotFoundException(technicianId);
        }
        return technician;
    }

    private boolean isValidTransition(InterventionStatus current, InterventionStatus next) {
        return switch (current) {
            case SCHEDULED -> next == InterventionStatus.IN_PROGRESS;
            case IN_PROGRESS -> next == InterventionStatus.COMPLETED;
            case COMPLETED -> next == InterventionStatus.VALIDATED;
            case VALIDATED -> false;
        };
    }

    private String normalizeDescription(String description) {
        return Optional.ofNullable(description)
                .map(String::trim)
                .filter(str -> !str.isBlank())
                .orElse(null);
    }

    private java.math.BigDecimal normalizeCoordinate(Double value) {
        if (value == null) {
            return null;
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        return java.math.BigDecimal.valueOf(value);
    }

    private InterventionEntity initializeTechnician(InterventionEntity entity) {
        if (entity.getTechnician() != null) {
            entity.getTechnician().getFullName();
        }
        return entity;
    }

    public record InterventionFilters(
            String query,
            InterventionStatus status,
            InterventionAssignmentMode assignmentMode,
            Long technicianId,
            Instant plannedFrom,
            Instant plannedTo) {}

    public record CreateInterventionCommand(
            String reference,
            String title,
            String description,
            Instant plannedAt,
            InterventionAssignmentMode assignmentMode,
            Long technicianId,
            Double latitude,
            Double longitude) {

        public CreateInterventionCommand {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(plannedAt, "plannedAt");
            Objects.requireNonNull(assignmentMode, "assignmentMode");
        }
    }

    public record UpdateInterventionCommand(
            String title,
            String description,
            Instant plannedAt,
            InterventionAssignmentMode assignmentMode,
            Long technicianId,
            Double latitude,
            Double longitude) {

        public UpdateInterventionCommand {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(plannedAt, "plannedAt");
            Objects.requireNonNull(assignmentMode, "assignmentMode");
        }
    }
}
