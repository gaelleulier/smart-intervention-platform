package io.smartip.interventions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.smartip.domain.InterventionAssignmentMode;
import io.smartip.domain.InterventionEntity;
import io.smartip.domain.InterventionRepository;
import io.smartip.domain.InterventionStatus;
import io.smartip.domain.UserEntity;
import io.smartip.domain.UserRepository;
import io.smartip.domain.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InterventionDemoSimulatorTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private InterventionRepository interventionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Captor
    private ArgumentCaptor<List<InterventionEntity>> interventionsCaptor;

    private List<UserEntity> technicians;
    private Map<Long, UserEntity> technicianLookup;

    @BeforeEach
    void setUp() {
        when(interventionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        technicians = List.of(
                technician(101L, "amandine.dupont@sip.local", "Amandine Dupont"),
                technician(102L, "karim.leroy@sip.local", "Karim Leroy"));
        technicianLookup = technicians.stream().collect(Collectors.toMap(UserEntity::getId, tech -> tech));
        when(userRepository.findByRoleOrderByIdAsc(UserRole.TECH)).thenReturn(technicians);
        technicianLookup.forEach((id, tech) -> when(userRepository.getReferenceById(id)).thenReturn(tech));
    }

    @Test
    void runSimulationGeneratesSyntheticDataAndPurges() {
        when(jdbcTemplate.update(InterventionDemoSimulator.PURGE_STALE_INTERVENTIONS_SQL, 500, 10))
                .thenReturn(10, 10, 0);

        InterventionDemoSimulator simulator = new InterventionDemoSimulator(
                interventionRepository, userRepository, jdbcTemplate, FIXED_CLOCK, new Random(42), 500, 10);

        simulator.runSimulation();

        verify(jdbcTemplate, times(1)).execute(InterventionDemoSimulator.CREATE_INDEX_SQL);
        verify(jdbcTemplate, times(3))
                .update(InterventionDemoSimulator.PURGE_STALE_INTERVENTIONS_SQL, 500, 10);

        verify(interventionRepository).saveAll(interventionsCaptor.capture());
        List<InterventionEntity> generated = interventionsCaptor.getValue();

        assertThat(generated).hasSizeBetween(0, 3);

        Instant now = FIXED_CLOCK.instant();
        Instant lowerBound = now.minus(Duration.ofMinutes(30));

        double latMin = readStaticDouble("LAT_MIN");
        double latMax = readStaticDouble("LAT_MAX");
        double lonMin = readStaticDouble("LON_MIN");
        double lonMax = readStaticDouble("LON_MAX");

        for (InterventionEntity entity : generated) {
            assertThat(entity.getReference()).startsWith("DEMO-");
            assertThat(entity.getTitle()).isNotBlank();
            assertThat(entity.getDescription()).containsIgnoringCase("demo");
            assertThat(entity.getStatus()).isNotNull();
            assertThat(entity.getAssignmentMode()).isEqualTo(InterventionAssignmentMode.MANUAL);
            assertThat(entity.getPlannedAt()).isAfterOrEqualTo(lowerBound).isBeforeOrEqualTo(now);
            assertThat(entity.getCreatedAt()).isAfterOrEqualTo(lowerBound).isBeforeOrEqualTo(now);
            assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(entity.getCreatedAt()).isBeforeOrEqualTo(now);
            assertThat(entity.getTechnician()).isNotNull();
            assertThat(entity.getTechnician()).isIn(technicians);
            assertThat(entity.getLatitude()).isNotNull();
            assertThat(entity.getLatitude().doubleValue()).isBetween(latMin, latMax);
            assertThat(entity.getLongitude()).isNotNull();
            assertThat(entity.getLongitude().doubleValue()).isBetween(lonMin, lonMax);

            InterventionStatus status = entity.getStatus();
            switch (status) {
                case SCHEDULED -> {
                    assertThat(entity.getStartedAt()).isNull();
                    assertThat(entity.getCompletedAt()).isNull();
                    assertThat(entity.getValidatedAt()).isNull();
                }
                case IN_PROGRESS -> {
                    assertThat(entity.getStartedAt())
                            .isAfterOrEqualTo(entity.getPlannedAt())
                            .isBeforeOrEqualTo(now);
                    assertThat(entity.getCompletedAt()).isNull();
                    assertThat(entity.getValidatedAt()).isNull();
                }
                case COMPLETED -> {
                    assertThat(entity.getStartedAt())
                            .isAfterOrEqualTo(entity.getPlannedAt())
                            .isBeforeOrEqualTo(now);
                    assertThat(entity.getCompletedAt())
                            .isAfterOrEqualTo(entity.getStartedAt())
                            .isBeforeOrEqualTo(now);
                    assertThat(entity.getValidatedAt()).isNull();
                }
                case VALIDATED -> {
                    assertThat(entity.getStartedAt())
                            .isAfterOrEqualTo(entity.getPlannedAt())
                            .isBeforeOrEqualTo(now);
                    assertThat(entity.getCompletedAt())
                            .isAfterOrEqualTo(entity.getStartedAt())
                            .isBeforeOrEqualTo(now);
                    assertThat(entity.getValidatedAt())
                            .isAfterOrEqualTo(entity.getCompletedAt())
                            .isBeforeOrEqualTo(now);
                }
            }
        }
    }

    @Test
    void indexCreationExecutedOnlyOnce() {
        when(jdbcTemplate.update(InterventionDemoSimulator.PURGE_STALE_INTERVENTIONS_SQL, 500, 10))
                .thenReturn(0);

        InterventionDemoSimulator simulator = new InterventionDemoSimulator(
                interventionRepository, userRepository, jdbcTemplate, FIXED_CLOCK, new Random(7), 500, 10);

        simulator.runSimulation();
        simulator.runSimulation();

        verify(jdbcTemplate, times(1)).execute(InterventionDemoSimulator.CREATE_INDEX_SQL);
    }

    private double readStaticDouble(String fieldName) {
        return (double) Objects.requireNonNull(
                ReflectionTestUtils.getField(InterventionDemoSimulator.class, fieldName));
    }

    private UserEntity technician(Long id, String email, String fullName) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(UserRole.TECH);
        user.setPasswordHash("hash");
        user.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return user;
    }
}
