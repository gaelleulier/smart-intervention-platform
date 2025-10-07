package io.smartip.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsAggregationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyticsAggregationService.class);

    private final JdbcTemplate jdbcTemplate;
    private final DashboardCacheEvictor cacheEvictor;
    private final int historyDays;

    public AnalyticsAggregationService(
            JdbcTemplate jdbcTemplate,
            DashboardCacheEvictor cacheEvictor,
            @Value("${dashboard.analytics.history-days:14}") int historyDays) {
        this.jdbcTemplate = jdbcTemplate;
        this.cacheEvictor = cacheEvictor;
        this.historyDays = Math.max(historyDays, 1);
    }

    @Transactional
    public void refreshAnalytics() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(historyDays - 1L);
        Instant now = Instant.now();

        try {
            refreshDailyMetrics(start, today, now);
            refreshTechnicianLoad(now);
            refreshGeoView(now);
            cacheEvictor.evictAll();
            LOGGER.info("Dashboard analytics refreshed successfully.");
        } catch (DataAccessException ex) {
            LOGGER.error("Failed to refresh dashboard analytics", ex);
            throw ex;
        }
    }

    @Scheduled(fixedDelayString = "${dashboard.analytics.refresh-interval:300000}")
    public void scheduledRefresh() {
        refreshAnalytics();
    }

    private void refreshDailyMetrics(LocalDate from, LocalDate to, Instant refreshedAt) {
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        jdbcTemplate.update(
                "DELETE FROM analytics.intervention_daily_metrics WHERE metric_date BETWEEN ? AND ?",
                java.sql.Date.valueOf(from),
                java.sql.Date.valueOf(to));

        java.sql.Timestamp fromInstant = java.sql.Timestamp.from(from.atStartOfDay().toInstant(ZoneOffset.UTC));
        java.sql.Timestamp toInstant = java.sql.Timestamp.from(to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        java.sql.Timestamp refreshedTimestamp = java.sql.Timestamp.from(refreshedAt);

        jdbcTemplate.update(
                """
                INSERT INTO analytics.intervention_daily_metrics (
                    metric_date,
                    status,
                    total_count,
                    avg_completion_seconds,
                    validation_ratio,
                    last_refreshed_at)
                WITH base AS (
                    SELECT
                        (planned_at AT TIME ZONE 'UTC')::date AS metric_date,
                        status,
                        CASE
                            WHEN completed_at IS NOT NULL AND started_at IS NOT NULL
                                THEN EXTRACT(EPOCH FROM completed_at - started_at)
                            ELSE NULL
                        END AS completion_seconds
                    FROM interventions
                    WHERE planned_at >= ? AND planned_at < ?
                ),
                aggregated AS (
                    SELECT
                        metric_date,
                        status,
                        COUNT(*) AS total_count,
                        AVG(completion_seconds) AS avg_completion_seconds
                    FROM base
                    GROUP BY metric_date, status
                ),
                daily_completed AS (
                    SELECT
                        metric_date,
                        SUM(CASE WHEN status IN ('COMPLETED', 'VALIDATED') THEN total_count ELSE 0 END) AS completed_total,
                        SUM(CASE WHEN status = 'VALIDATED' THEN total_count ELSE 0 END) AS validated_total
                    FROM aggregated
                    GROUP BY metric_date
                )
                SELECT
                    a.metric_date,
                    a.status,
                    a.total_count,
                    a.avg_completion_seconds,
                    CASE
                        WHEN a.status = 'VALIDATED'
                            THEN CASE
                                     WHEN dc.completed_total = 0 THEN NULL
                                     ELSE (dc.validated_total::numeric / dc.completed_total::numeric) * 100
                                 END
                        ELSE NULL
                    END AS validation_ratio,
                    ?
                FROM aggregated a
                JOIN daily_completed dc ON dc.metric_date = a.metric_date
                """,
                fromInstant,
                toInstant,
                refreshedTimestamp);
    }

    private void refreshTechnicianLoad(Instant refreshedAt) {
        jdbcTemplate.update("DELETE FROM analytics.intervention_technician_load");
        jdbcTemplate.update(
                """
                INSERT INTO analytics.intervention_technician_load (
                    technician_id,
                    open_count,
                    completed_today,
                    avg_completion_seconds,
                    last_refreshed_at)
                SELECT
                    technician_id,
                    COUNT(*) FILTER (WHERE status IN ('SCHEDULED','IN_PROGRESS')) AS open_count,
                    COUNT(*) FILTER (
                        WHERE status IN ('COMPLETED','VALIDATED')
                          AND completed_at >= date_trunc('day', now())
                          AND completed_at < date_trunc('day', now()) + INTERVAL '1 day') AS completed_today,
                    AVG(EXTRACT(EPOCH FROM completed_at - started_at))
                        FILTER (WHERE completed_at IS NOT NULL AND started_at IS NOT NULL) AS avg_completion_seconds,
                    ?
                FROM interventions
                WHERE technician_id IS NOT NULL
                GROUP BY technician_id
                """,
                java.sql.Timestamp.from(refreshedAt));
    }

    private void refreshGeoView(Instant refreshedAt) {
        jdbcTemplate.update("DELETE FROM analytics.intervention_geo_view");
        jdbcTemplate.update(
                """
                INSERT INTO analytics.intervention_geo_view (
                    intervention_id,
                    latitude,
                    longitude,
                    status,
                    technician_id,
                    planned_at,
                    updated_at)
                SELECT
                    id,
                    latitude,
                    longitude,
                    status,
                    technician_id,
                    planned_at,
                    COALESCE(updated_at, ?)
                FROM interventions
                WHERE latitude IS NOT NULL
                  AND longitude IS NOT NULL
                """,
                java.sql.Timestamp.from(refreshedAt));
    }
}
