package io.smartip.dashboard;

import io.smartip.dashboard.DashboardRepository.DailyMetricRow;
import io.smartip.dashboard.DashboardRepository.TechnicianLoadRow;
import io.smartip.dashboard.dto.DashboardSummaryResponse;
import io.smartip.dashboard.dto.InterventionMapMarker;
import io.smartip.dashboard.dto.StatusTrendPoint;
import io.smartip.dashboard.dto.TechnicianLoadResponse;
import io.smartip.domain.UserEntity;
import io.smartip.domain.UserRepository;
import io.smartip.domain.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final int MAP_DEFAULT_LIMIT = 500;

    private final DashboardRepository repository;
    private final UserRepository userRepository;

    public DashboardService(DashboardRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Cacheable(cacheNames = "dashboard-summary")
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(LocalDate date) {
        Map<String, DailyMetricRow> metrics = repository.fetchDailyMetrics(date);
        long scheduled = metrics.getOrDefault("SCHEDULED", zeroRow("SCHEDULED")).count();
        long inProgress = metrics.getOrDefault("IN_PROGRESS", zeroRow("IN_PROGRESS")).count();
        long completed = metrics.getOrDefault("COMPLETED", zeroRow("COMPLETED")).count();
        long validated = metrics.getOrDefault("VALIDATED", zeroRow("VALIDATED")).count();

        long total = scheduled + inProgress + completed + validated;
        Double avgCompletionSeconds = metrics.getOrDefault("COMPLETED", zeroRow("COMPLETED")).averageCompletionSeconds();
        Double validationRatio = metrics.getOrDefault("VALIDATED", zeroRow("VALIDATED")).validationRatio();

        Instant refreshedAt = metrics.values().stream()
                .map(DailyMetricRow::refreshedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new DashboardSummaryResponse(
                total,
                scheduled,
                inProgress,
                completed,
                validated,
                avgCompletionSeconds,
                validationRatio,
                refreshedAt);
    }

    @Cacheable(cacheNames = "dashboard-status-trends")
    @Transactional(readOnly = true)
    public List<StatusTrendPoint> getStatusTrends(LocalDate from, LocalDate to) {
        return repository.fetchStatusTrends(from, to);
    }

    @Cacheable(cacheNames = "dashboard-technician-load")
    @Transactional(readOnly = true)
    public List<TechnicianLoadResponse> getTechnicianLoad(String requesterEmail, UserRole requesterRole) {
        boolean canViewAll = requesterRole == UserRole.ADMIN || requesterRole == UserRole.DISPATCHER;
        List<TechnicianLoadRow> rows;
        if (canViewAll) {
            rows = repository.fetchTechnicianLoads();
        } else {
            UserEntity technician = userRepository
                    .findByEmailIgnoreCase(requesterEmail)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown technician " + requesterEmail));
            rows = repository.fetchTechnicianLoad(technician.getId());
        }
        return rows.stream()
                .map(row -> new TechnicianLoadResponse(
                        row.technicianId(),
                        row.fullName(),
                        row.email(),
                        row.openCount(),
                        row.completedToday(),
                        row.averageCompletionSeconds(),
                        row.lastRefreshedAt() != null ? row.lastRefreshedAt().atOffset(ZoneOffset.UTC).toString() : null))
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = "dashboard-map")
    @Transactional(readOnly = true)
    public List<InterventionMapMarker> getMapMarkers(List<String> statuses, boolean preciseCoordinates, int limit) {
        List<String> normalizedStatuses = Optional.ofNullable(statuses)
                .orElse(List.of())
                .stream()
                .map(status -> status.trim().toUpperCase(Locale.ROOT))
                .filter(status -> !status.isBlank())
                .collect(Collectors.toList());

        int cappedLimit = limit > 0 ? Math.min(limit, MAP_DEFAULT_LIMIT) : MAP_DEFAULT_LIMIT;

        List<InterventionMapMarker> rawMarkers = repository.fetchMapMarkers(normalizedStatuses, cappedLimit);
        if (preciseCoordinates) {
            return rawMarkers;
        }
        return rawMarkers.stream()
                .map(marker -> new InterventionMapMarker(
                        marker.interventionId(),
                        round(marker.latitude(), 2),
                        round(marker.longitude(), 2),
                        marker.status(),
                        marker.technicianId(),
                        marker.plannedAt(),
                        marker.updatedAt()))
                .collect(Collectors.toList());
    }

    private DailyMetricRow zeroRow(String status) {
        return new DailyMetricRow(status, 0L, null, null, null);
    }

    private double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }
}
