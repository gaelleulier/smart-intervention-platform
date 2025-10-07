package io.smartip.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.smartip.dashboard.DashboardRepository.DailyMetricRow;
import io.smartip.dashboard.DashboardRepository.TechnicianLoadRow;
import io.smartip.dashboard.dto.DashboardSummaryResponse;
import io.smartip.dashboard.dto.InterventionMapMarker;
import io.smartip.dashboard.dto.TechnicianLoadResponse;
import io.smartip.domain.UserEntity;
import io.smartip.domain.UserRepository;
import io.smartip.domain.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DashboardRepository repository;

    @Mock
    private UserRepository userRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(repository, userRepository);
    }

    @Test
    void getSummaryAggregatesStatusCounts() {
        LocalDate date = LocalDate.of(2025, 10, 7);
        Instant refreshed = Instant.parse("2025-10-07T10:00:00Z");
        when(repository.fetchDailyMetrics(date)).thenReturn(Map.of(
                "SCHEDULED", new DailyMetricRow("SCHEDULED", 5, null, null, refreshed),
                "IN_PROGRESS", new DailyMetricRow("IN_PROGRESS", 3, null, null, refreshed),
                "COMPLETED", new DailyMetricRow("COMPLETED", 7, 540.5, null, refreshed),
                "VALIDATED", new DailyMetricRow("VALIDATED", 6, null, 87.5, refreshed)));

        DashboardSummaryResponse summary = service.getSummary(date);

        assertThat(summary.totalInterventions()).isEqualTo(21);
        assertThat(summary.scheduledCount()).isEqualTo(5);
        assertThat(summary.inProgressCount()).isEqualTo(3);
        assertThat(summary.completedCount()).isEqualTo(7);
        assertThat(summary.validatedCount()).isEqualTo(6);
        assertThat(summary.averageCompletionSeconds()).isEqualTo(540.5);
        assertThat(summary.validationRatio()).isEqualTo(87.5);
        assertThat(summary.lastRefreshedAt()).isEqualTo(refreshed);
    }

    @Test
    void technicianLoadReturnsAllForAdmin() {
        Instant refreshed = Instant.parse("2025-10-07T11:00:00Z");
        when(repository.fetchTechnicianLoads()).thenReturn(List.of(
                new TechnicianLoadRow(1L, "Alice Tech", "alice@example.com", 2, 4, 600.0, refreshed)));

        List<TechnicianLoadResponse> responses = service.getTechnicianLoad("admin@example.com", UserRole.ADMIN);

        assertThat(responses).hasSize(1);
        TechnicianLoadResponse response = responses.get(0);
        assertThat(response.technicianId()).isEqualTo(1L);
        assertThat(response.openCount()).isEqualTo(2);
        assertThat(response.completedToday()).isEqualTo(4);
        assertThat(response.averageCompletionSeconds()).isEqualTo(600.0);
        verify(repository).fetchTechnicianLoads();
    }

    @Test
    void technicianLoadFiltersForTechnician() {
        UserEntity tech = new UserEntity();
        tech.setId(7L);
        tech.setEmail("tech@example.com");
        tech.setRole(UserRole.TECH);
        when(userRepository.findByEmailIgnoreCase("tech@example.com")).thenReturn(Optional.of(tech));
        when(repository.fetchTechnicianLoad(7L)).thenReturn(List.of(
                new TechnicianLoadRow(7L, "Tech Seven", "tech@example.com", 1, 2, 480.0, Instant.now())));

        List<TechnicianLoadResponse> responses = service.getTechnicianLoad("tech@example.com", UserRole.TECH);

        assertThat(responses).hasSize(1);
        verify(repository).fetchTechnicianLoad(7L);
    }

    @Test
    void getMapMarkersRoundsForNonAdmin() {
        List<InterventionMapMarker> markers = List.of(
                new InterventionMapMarker(1L, 48.856613, 2.352222, "IN_PROGRESS", 3L, Instant.now(), Instant.now()));
        when(repository.fetchMapMarkers(any(), eq(500))).thenReturn(markers);

        List<InterventionMapMarker> rounded = service.getMapMarkers(List.of("in_progress"), false, 0);

        assertThat(rounded.get(0).latitude()).isEqualTo(48.86);
        assertThat(rounded.get(0).longitude()).isEqualTo(2.35);
        verify(repository).fetchMapMarkers(List.of("IN_PROGRESS"), 500);
    }

    @Test
    void getMapMarkersKeepsPrecisionForAdmins() {
        List<InterventionMapMarker> markers = List.of(
                new InterventionMapMarker(1L, 48.856613, 2.352222, "IN_PROGRESS", 3L, Instant.now(), Instant.now()));
        when(repository.fetchMapMarkers(any(), eq(200))).thenReturn(markers);

        List<InterventionMapMarker> raw = service.getMapMarkers(List.of("IN_PROGRESS"), true, 200);

        assertThat(raw.get(0).latitude()).isEqualTo(48.856613);
        assertThat(raw.get(0).longitude()).isEqualTo(2.352222);
    }

    @Test
    void technicianLoadThrowsWhenUserMissing() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getTechnicianLoad("ghost@example.com", UserRole.TECH));
    }
}
