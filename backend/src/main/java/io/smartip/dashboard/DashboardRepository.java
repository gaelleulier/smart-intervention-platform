package io.smartip.dashboard;

import io.smartip.dashboard.dto.InterventionMapMarker;
import io.smartip.dashboard.dto.StatusTrendPoint;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class DashboardRepository {

    private static final String DAILY_METRICS_QUERY = """
            SELECT status,
                   total_count,
                   avg_completion_seconds,
                   validation_ratio,
                   last_refreshed_at
            FROM analytics.intervention_daily_metrics
            WHERE metric_date = ?
            """;

    private static final String DAILY_METRICS_TECHNICIAN_QUERY = """
            WITH base AS (
                SELECT
                    status,
                    CASE
                        WHEN completed_at IS NOT NULL AND started_at IS NOT NULL
                            THEN EXTRACT(EPOCH FROM completed_at - started_at)
                        ELSE NULL
                    END AS completion_seconds,
                    GREATEST(
                        COALESCE(validated_at, 'epoch'::timestamptz),
                        COALESCE(completed_at, 'epoch'::timestamptz),
                        COALESCE(updated_at, 'epoch'::timestamptz),
                        COALESCE(started_at, 'epoch'::timestamptz),
                        created_at
                    ) AS event_instant
                FROM interventions
                WHERE technician_id = ?
                  AND CAST(COALESCE(planned_at, started_at, created_at) AS DATE) = ?
            ),
            aggregated AS (
                SELECT
                    status,
                    COUNT(*) AS total_count,
                    AVG(completion_seconds) AS avg_completion_seconds,
                    MAX(event_instant) AS last_event_instant
                FROM base
                GROUP BY status
            ),
            daily_completed AS (
                SELECT
                    SUM(CASE WHEN status IN ('COMPLETED','VALIDATED') THEN total_count ELSE 0 END) AS completed_total,
                    SUM(CASE WHEN status = 'VALIDATED' THEN total_count ELSE 0 END) AS validated_total,
                    MAX(last_event_instant) AS last_event_instant
                FROM aggregated
            )
            SELECT
                a.status,
                a.total_count,
                a.avg_completion_seconds,
                CASE
                    WHEN a.status = 'VALIDATED' AND dc.completed_total > 0
                        THEN (dc.validated_total::numeric / dc.completed_total::numeric) * 100
                    ELSE NULL
                END AS validation_ratio,
                dc.last_event_instant AS last_refreshed_at
            FROM aggregated a
            CROSS JOIN daily_completed dc
            """;

    private static final String STATUS_TRENDS_QUERY = """
            SELECT metric_date,
                   status,
                   total_count
            FROM analytics.intervention_daily_metrics
            WHERE metric_date BETWEEN ? AND ?
            ORDER BY metric_date ASC, status ASC
            """;

    private static final String STATUS_TRENDS_TECHNICIAN_QUERY = """
            SELECT
                CAST(COALESCE(planned_at, started_at, created_at) AS DATE) AS metric_date,
                status,
                COUNT(*) AS total_count
            FROM interventions
            WHERE technician_id = ?
              AND CAST(COALESCE(planned_at, started_at, created_at) AS DATE) BETWEEN ? AND ?
            GROUP BY metric_date, status
            ORDER BY metric_date ASC, status ASC
            """;

    private static final String TECHNICIAN_LOAD_QUERY_BASE = """
            SELECT t.technician_id,
                   u.full_name,
                   u.email,
                   t.open_count,
                   t.completed_today,
                   t.avg_completion_seconds,
                   t.last_refreshed_at
            FROM analytics.intervention_technician_load t
            JOIN users u ON u.id = t.technician_id
            """;

    private static final String TECHNICIAN_LOAD_ORDER = " ORDER BY t.open_count DESC, u.full_name ASC";

    private static final String MAP_QUERY_BASE = """
            SELECT intervention_id,
                   latitude,
                   longitude,
                   status,
                   technician_id,
                   planned_at,
                   updated_at
            FROM analytics.intervention_geo_view
            """;

    private final JdbcTemplate jdbcTemplate;

    DashboardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Map<String, DailyMetricRow> fetchDailyMetrics(LocalDate date, Long technicianId) {
        List<DailyMetricRow> rows;
        if (technicianId == null) {
            rows = jdbcTemplate.query(DAILY_METRICS_QUERY, this::mapDailyMetric, date);
        } else {
            rows = jdbcTemplate.query(
                    DAILY_METRICS_TECHNICIAN_QUERY,
                    this::mapDailyMetric,
                    technicianId,
                    java.sql.Date.valueOf(date));
        }
        Map<String, DailyMetricRow> byStatus = new HashMap<>(rows.size());
        for (DailyMetricRow row : rows) {
            byStatus.put(row.status(), row);
        }
        return byStatus;
    }

    List<StatusTrendPoint> fetchStatusTrends(LocalDate from, LocalDate to, Long technicianId) {
        if (technicianId == null) {
            return jdbcTemplate.query(
                    STATUS_TRENDS_QUERY,
                    (rs, rowNum) -> new StatusTrendPoint(
                            rs.getObject("metric_date", LocalDate.class),
                            rs.getString("status"),
                            rs.getLong("total_count")),
                    from,
                    to);
        }
        return jdbcTemplate.query(
                STATUS_TRENDS_TECHNICIAN_QUERY,
                (rs, rowNum) -> new StatusTrendPoint(
                        rs.getObject("metric_date", LocalDate.class),
                        rs.getString("status"),
                        rs.getLong("total_count")),
                technicianId,
                from,
                to);
    }

    List<TechnicianLoadRow> fetchTechnicianLoads() {
        return jdbcTemplate.query(TECHNICIAN_LOAD_QUERY_BASE + TECHNICIAN_LOAD_ORDER, this::mapTechnicianLoad);
    }

    List<TechnicianLoadRow> fetchTechnicianLoad(long technicianId) {
        return jdbcTemplate.query(
                TECHNICIAN_LOAD_QUERY_BASE + " WHERE t.technician_id = ?" + TECHNICIAN_LOAD_ORDER,
                this::mapTechnicianLoad,
                technicianId);
    }

    List<InterventionMapMarker> fetchMapMarkers(List<String> statuses, Long technicianId, int limit) {
        StringBuilder sql = new StringBuilder(MAP_QUERY_BASE);
        boolean whereAdded = false;
        if (statuses != null && !statuses.isEmpty()) {
            sql.append("WHERE status IN (");
            sql.append(String.join(", ", java.util.Collections.nCopies(statuses.size(), "?")));
            sql.append(") ");
            whereAdded = true;
        }
        if (technicianId != null) {
            sql.append(whereAdded ? "AND " : "WHERE ");
            sql.append("technician_id = ? ");
            whereAdded = true;
        }
        sql.append("ORDER BY updated_at DESC LIMIT ?");

        java.util.List<Object> params = new java.util.ArrayList<>();
        java.util.List<Integer> types = new java.util.ArrayList<>();
        if (statuses != null && !statuses.isEmpty()) {
            for (String status : statuses) {
                params.add(status);
                types.add(java.sql.Types.VARCHAR);
            }
        }
        if (technicianId != null) {
            params.add(technicianId);
            types.add(java.sql.Types.BIGINT);
        }
        params.add(limit);
        types.add(java.sql.Types.INTEGER);

        return jdbcTemplate.query(
                sql.toString(),
                params.toArray(),
                types.stream().mapToInt(Integer::intValue).toArray(),
                mapInterventionMarker());
    }

    private DailyMetricRow mapDailyMetric(ResultSet rs, int rowNum) throws SQLException {
        return new DailyMetricRow(
                rs.getString("status"),
                rs.getLong("total_count"),
                getNullableDouble(rs, "avg_completion_seconds"),
                getNullableDouble(rs, "validation_ratio"),
                getInstant(rs, "last_refreshed_at"));
    }

    private TechnicianLoadRow mapTechnicianLoad(ResultSet rs, int rowNum) throws SQLException {
        return new TechnicianLoadRow(
                rs.getLong("technician_id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getLong("open_count"),
                rs.getLong("completed_today"),
                getNullableDouble(rs, "avg_completion_seconds"),
                getInstant(rs, "last_refreshed_at"));
    }

    private RowMapper<InterventionMapMarker> mapInterventionMarker() {
        return (rs, rowNum) -> new InterventionMapMarker(
                rs.getLong("intervention_id"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getString("status"),
                getNullableLong(rs, "technician_id"),
                getInstant(rs, "planned_at"),
                getInstant(rs, "updated_at"));
    }

    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Instant getInstant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp != null ? timestamp.toInstant() : null;
    }

    record DailyMetricRow(
            String status, long count, Double averageCompletionSeconds, Double validationRatio, Instant refreshedAt) {}

    record TechnicianLoadRow(
            long technicianId,
            String fullName,
            String email,
            long openCount,
            long completedToday,
            Double averageCompletionSeconds,
            Instant lastRefreshedAt) {}
}
