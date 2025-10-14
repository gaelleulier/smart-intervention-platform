package io.smartip.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InterventionRepository
        extends JpaRepository<InterventionEntity, Long>, JpaSpecificationExecutor<InterventionEntity> {

    boolean existsByReferenceIgnoreCase(String reference);

    Optional<InterventionEntity> findByReferenceIgnoreCase(String reference);

    List<InterventionEntity> findAllByTechnician_Id(Long technicianId);

    List<InterventionEntity> findAllByPlannedAtBetween(Instant from, Instant to);

    long countByTechnician_IdAndStatusIn(Long technicianId, Collection<InterventionStatus> statuses);

    java.util.Optional<InterventionEntity> findFirstByTechnician_IdAndLatitudeIsNotNullAndLongitudeIsNotNullOrderByUpdatedAtDesc(Long technicianId);

    List<InterventionEntity> findTop20ByTechnician_IdAndStatusInOrderByUpdatedAtDesc(Long technicianId, Collection<InterventionStatus> statuses);
}
