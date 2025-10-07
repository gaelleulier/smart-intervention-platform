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

    private static final String STATUS_TRENDS_QUERY = """
            SELECT metric_date,
                   status,
                   total_count
            FROM analytics.intervention_daily_metrics
            WHERE metric_date BETWEEN ? AND ?
            ORDER BY metric_date ASC, status ASC
            """;

    private static final String TECHNICIAN_LOAD_QUERY = """
            SELECT t.technician_id,
                   u.full_name,
                   u.email,
                   t.open_count,
                   t.completed_today,
                   t.avg_completion_seconds,
                   t.last_refreshed_at
            FROM analytics.intervention_technician_load t
            JOIN users u ON u.id = t.technician_id
            ORDER BY t.open_count DESC, u.full_name ASC
            """;

    private static final String TECHNICIAN_LOAD_BY_ID_QUERY = TECHNICIAN_LOAD_QUERY + " WHERE t.technician_id = ?";

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

    Map<String, DailyMetricRow> fetchDailyMetrics(LocalDate date) {
        List<DailyMetricRow> rows = jdbcTemplate.query(DAILY_METRICS_QUERY, this::mapDailyMetric, date);
        Map<String, DailyMetricRow> byStatus = new HashMap<>(rows.size());
        for (DailyMetricRow row : rows) {
            byStatus.put(row.status(), row);
        }
        return byStatus;
    }

    List<StatusTrendPoint> fetchStatusTrends(LocalDate from, LocalDate to) {
        return jdbcTemplate.query(
                STATUS_TRENDS_QUERY,
                (rs, rowNum) -> new StatusTrendPoint(
                        rs.getObject("metric_date", LocalDate.class),
                        rs.getString("status"),
                        rs.getLong("total_count")),
                from,
                to);
    }

    List<TechnicianLoadRow> fetchTechnicianLoads() {
        return jdbcTemplate.query(TECHNICIAN_LOAD_QUERY, this::mapTechnicianLoad);
    }

    List<TechnicianLoadRow> fetchTechnicianLoad(long technicianId) {
        return jdbcTemplate.query(TECHNICIAN_LOAD_BY_ID_QUERY, this::mapTechnicianLoad, technicianId);
    }

    List<InterventionMapMarker> fetchMapMarkers(List<String> statuses, int limit) {
        StringBuilder sql = new StringBuilder(MAP_QUERY_BASE);
        newLine(sql);
        newLine(sql);
        if (statuses != null && !statuses.isEmpty()) {
            sql.append("WHERE status IN (");
            sql.append(String.join(", ", java.util.Collections.nCopies(statuses.size(), "?")));
            sql.append(") ");
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
        params.add(limit);
        types.add(java.sql.Types.INTEGER);

        return jdbcTemplate.query(
                sql.toString(),
                params.toArray(),
                types.stream().mapToInt(Integer::intValue).toArray(),
                mapInterventionMarker());
    }

    private void newLine(StringBuilder builder) {
        builder.append(System.lineSeparator());
    }

    private DailyMetricRow mapDailyMetric(ResultSet rs, int rowNum) throws SQLException {
        return new DailyMetricRow(
                rs.getString("status"),
                rs.getLong("total_count"),
                getNullableDouble(rs, "avg_completion_seconds"),
                getNullableDouble(rs, "validation_ratio"),
                rs.getObject("last_refreshed_at", Instant.class));
    }

    private TechnicianLoadRow mapTechnicianLoad(ResultSet rs, int rowNum) throws SQLException {
        return new TechnicianLoadRow(
                rs.getLong("technician_id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getLong("open_count"),
                rs.getLong("completed_today"),
                getNullableDouble(rs, "avg_completion_seconds"),
                rs.getObject("last_refreshed_at", Instant.class));
    }

    private RowMapper<InterventionMapMarker> mapInterventionMarker() {
        return (rs, rowNum) -> new InterventionMapMarker(
                rs.getLong("intervention_id"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getString("status"),
                getNullableLong(rs, "technician_id"),
                rs.getObject("planned_at", Instant.class),
                rs.getObject("updated_at", Instant.class));
    }

    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
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
