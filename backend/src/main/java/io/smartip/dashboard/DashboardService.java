package io.smartip.dashboard;

import io.smartip.dashboard.DashboardRepository.DailyMetricRow;
import io.smartip.dashboard.dto.DashboardSummaryResponse;
import io.smartip.dashboard.dto.InterventionMapMarker;
import io.smartip.dashboard.dto.StatusTrendPoint;
import io.smartip.dashboard.dto.TechnicianLoadResponse;
import io.smartip.dashboard.dto.AiInsightResponse;
import io.smartip.dashboard.dto.ForecastPointResponse;
import io.smartip.dashboard.dto.ForecastResponse;
import io.smartip.domain.UserEntity;
import io.smartip.domain.UserRepository;
import io.smartip.domain.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    public DashboardSummaryResponse getSummary(LocalDate date, String requesterEmail, UserRole requesterRole) {
        Long technicianId = resolveTechnicianId(requesterEmail, requesterRole).orElse(null);
        Map<String, DailyMetricRow> metrics = repository.fetchDailyMetrics(date, technicianId);
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
    public List<StatusTrendPoint> getStatusTrends(LocalDate from, LocalDate to, String requesterEmail, UserRole requesterRole) {
        Long technicianId = resolveTechnicianId(requesterEmail, requesterRole).orElse(null);
        return repository.fetchStatusTrends(from, to, technicianId);
    }

    @Cacheable(cacheNames = "dashboard-technician-load")
    @Transactional(readOnly = true)
    public List<TechnicianLoadResponse> getTechnicianLoad(String requesterEmail, UserRole requesterRole) {
        boolean canViewAll = requesterRole == UserRole.ADMIN || requesterRole == UserRole.DISPATCHER;
        List<TechnicianLoadSnapshot> rows;
        if (canViewAll) {
            rows = repository.fetchTechnicianLoadSnapshots();
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
    public List<InterventionMapMarker> getMapMarkers(
            List<String> statuses, boolean preciseCoordinates, int limit, String requesterEmail, UserRole requesterRole) {
        Long technicianId = resolveTechnicianId(requesterEmail, requesterRole).orElse(null);
        List<String> normalizedStatuses = Optional.ofNullable(statuses)
                .orElse(List.of())
                .stream()
                .map(status -> status.trim().toUpperCase(Locale.ROOT))
                .filter(status -> !status.isBlank())
                .collect(Collectors.toList());

        int cappedLimit = limit > 0 ? Math.min(limit, MAP_DEFAULT_LIMIT) : MAP_DEFAULT_LIMIT;

        List<InterventionMapMarker> rawMarkers =
                repository.fetchMapMarkers(normalizedStatuses, technicianId, cappedLimit);
        if (preciseCoordinates || requesterRole == UserRole.TECH) {
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

    @Transactional(readOnly = true)
    public AiInsightResponse getAiInsights(LocalDate date, String requesterEmail, UserRole requesterRole) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        Long technicianId = resolveTechnicianId(requesterEmail, requesterRole).orElse(null);

        Map<String, DailyMetricRow> todayMetrics = repository.fetchDailyMetrics(targetDate, technicianId);
        Map<String, DailyMetricRow> previousMetrics =
                repository.fetchDailyMetrics(targetDate.minusDays(1), technicianId);

        long todayTotal = todayMetrics.values().stream().mapToLong(DailyMetricRow::count).sum();
        long previousTotal = previousMetrics.values().stream().mapToLong(DailyMetricRow::count).sum();

        double trendPercentage;
        if (previousTotal == 0) {
            trendPercentage = todayTotal > 0 ? 100.0 : 0.0;
        } else {
            trendPercentage = ((double) (todayTotal - previousTotal) / previousTotal) * 100.0;
        }
        String trendDirection = trendPercentage > 2.5 ? "UP" : trendPercentage < -2.5 ? "DOWN" : "FLAT";

        Double validationRatio = todayMetrics.getOrDefault("VALIDATED", zeroRow("VALIDATED")).validationRatio();
        if (validationRatio == null) {
            long validatedCount = todayMetrics.getOrDefault("VALIDATED", zeroRow("VALIDATED")).count();
            long completedCount = todayMetrics.getOrDefault("COMPLETED", zeroRow("COMPLETED")).count();
            long denominator = Math.max(1, validatedCount + completedCount);
            validationRatio = (validatedCount * 100.0) / denominator;
        }
        validationRatio = Math.min(100.0, Math.max(0.0, validationRatio));

        Double completionAvg = Optional.ofNullable(todayMetrics.get("COMPLETED"))
                .map(DailyMetricRow::averageCompletionSeconds)
                .orElse(null);
        double slaThresholdSeconds = 4 * 60 * 60; // 4 hours
        String slaAssessment;
        if (completionAvg == null || completionAvg <= 0) {
            slaAssessment = "Pas assez de données pour le SLA";
        } else if (completionAvg <= slaThresholdSeconds) {
            slaAssessment = "SLA respectée";
        } else if (completionAvg <= slaThresholdSeconds * 1.15) {
            slaAssessment = "SLA proche du seuil";
        } else {
            slaAssessment = "SLA en risque";
        }

        String headline = switch (trendDirection) {
            case "UP" -> "La charge augmente aujourd'hui";
            case "DOWN" -> "Moins d'interventions que la veille";
            default -> "Volume stable par rapport à la veille";
        };

        List<String> highlights = new ArrayList<>();
        highlights.add(String.format(Locale.FRENCH, "Total interventions: %d (%+.1f%% vs veille)", todayTotal, trendPercentage));
        highlights.add(String.format(Locale.FRENCH, "Taux de validation: %.1f%%", validationRatio));
        if (completionAvg != null && completionAvg > 0) {
            highlights.add(String.format(Locale.FRENCH, "Durée moyenne de résolution: %.1f h", completionAvg / 3600.0));
        } else {
            highlights.add("Durée moyenne de résolution: N/A");
        }

        String validationAssessment =
                validationRatio >= 85 ? "Très bon niveau de validation" : validationRatio >= 70 ? "Validation à surveiller" : "Validation faible";

        return new AiInsightResponse(
                targetDate,
                headline,
                trendDirection,
                roundDouble(trendPercentage, 1),
                roundDouble(validationRatio, 1),
                validationAssessment,
                slaAssessment,
                highlights);
    }

    @Transactional(readOnly = true)
    public ForecastResponse getForecast(LocalDate date, String requesterEmail, UserRole requesterRole) {
        LocalDate endDate = date != null ? date : LocalDate.now();
        LocalDate startDate = endDate.minusDays(20);
        Long technicianId = resolveTechnicianId(requesterEmail, requesterRole).orElse(null);

        Map<LocalDate, Long> totals = repository.fetchDailyTotals(startDate, endDate, technicianId);

        double alpha = 0.5;
        List<LocalDate> orderedDates = new ArrayList<>();
        for (LocalDate cursor = startDate; !cursor.isAfter(endDate); cursor = cursor.plusDays(1)) {
            orderedDates.add(cursor);
        }

        double forecast = 0;
        boolean initialized = false;
        long lastObserved = 0;
        double totalSum = 0;
        int totalCount = 0;

        for (LocalDate current : orderedDates) {
            long actual = totals.getOrDefault(current, 0L);
            if (!initialized) {
                forecast = actual;
                initialized = true;
            } else {
                forecast = alpha * actual + (1 - alpha) * forecast;
            }
            lastObserved = actual;
            totalSum += actual;
            totalCount++;
        }

        double baselineAverage = totalCount > 0 ? totalSum / totalCount : 0;
        List<ForecastPointResponse> points = new ArrayList<>();
        double rollingForecast = forecast;
        long lastForPrediction = lastObserved;
        LocalDate cursor = endDate;
        for (int i = 1; i <= 7; i++) {
            rollingForecast = alpha * lastForPrediction + (1 - alpha) * rollingForecast;
            long predicted = Math.max(0, Math.round(rollingForecast));
            points.add(new ForecastPointResponse(cursor.plusDays(i), predicted));
            lastForPrediction = predicted;
        }

        return new ForecastResponse(
                Instant.now().truncatedTo(ChronoUnit.SECONDS),
                "simple-exponential-smoothing",
                alpha,
                lastObserved,
                roundDouble(baselineAverage, 1),
                points);
    }

    @Transactional(readOnly = true)
    public List<TechnicianLoadSnapshot> getAllTechnicianLoadSnapshots() {
        return repository.fetchTechnicianLoadSnapshots();
    }

    private DailyMetricRow zeroRow(String status) {
        return new DailyMetricRow(status, 0L, null, null, null);
    }

    private Optional<Long> resolveTechnicianId(String email, UserRole role) {
        if (role != UserRole.TECH) {
            return Optional.empty();
        }
        return userRepository
                .findByEmailIgnoreCase(email)
                .map(UserEntity::getId);
    }

    private double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    private double roundDouble(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }
}
