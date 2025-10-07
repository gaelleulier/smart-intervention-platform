-- Flink SQL job to aggregate intervention events into analytics tables.
SET 'table.exec.mini-batch.enabled' = 'true';
SET 'table.exec.mini-batch.allow-latency' = '5 s';
SET 'table.exec.mini-batch.size' = '5000';
SET 'execution.checkpointing.interval' = '60 s';

CREATE TABLE IF NOT EXISTS intervention_events (
    id BIGINT,
    reference STRING,
    title STRING,
    description STRING,
    status STRING,
    assignment_mode STRING,
    planned_at TIMESTAMP_LTZ(3),
    started_at TIMESTAMP_LTZ(3),
    completed_at TIMESTAMP_LTZ(3),
    validated_at TIMESTAMP_LTZ(3),
    technician_id BIGINT,
    latitude DOUBLE,
    longitude DOUBLE,
    created_at TIMESTAMP_LTZ(3),
    updated_at TIMESTAMP_LTZ(3),
    op STRING,
    source_ts_ms TIMESTAMP_LTZ(3),
    WATERMARK FOR source_ts_ms AS source_ts_ms - INTERVAL '5' SECOND
) WITH (
    'connector' = 'kafka',
    'topic' = 'sip.interventions',
    'properties.bootstrap.servers' = 'kafka:9092',
    'properties.group.id' = 'flink-interventions-consumer',
    'scan.startup.mode' = 'latest-offset',
    'format' = 'json',
    'json.fail-on-missing-field' = 'false',
    'json.ignore-parse-errors' = 'true'
);

CREATE TABLE IF NOT EXISTS analytics_intervention_daily_metrics (
    metric_date DATE,
    status STRING,
    total_count BIGINT,
    avg_completion_seconds DOUBLE,
    validation_ratio DOUBLE,
    last_refreshed_at TIMESTAMP_LTZ(3),
    PRIMARY KEY (metric_date, status) NOT ENFORCED
) WITH (
    'connector' = 'jdbc',
    'url' = 'jdbc:postgresql://db:5432/${POSTGRES_DB}',
    'table-name' = 'analytics.intervention_daily_metrics',
    'username' = '${POSTGRES_USER}',
    'password' = '${POSTGRES_PASSWORD}',
    'driver' = 'org.postgresql.Driver',
    'sink.buffer-flush.interval' = '5 s',
    'sink.buffer-flush.max-rows' = '1000'
);

CREATE TABLE IF NOT EXISTS analytics_intervention_technician_load (
    technician_id BIGINT,
    open_count BIGINT,
    completed_today BIGINT,
    avg_completion_seconds DOUBLE,
    last_refreshed_at TIMESTAMP_LTZ(3),
    PRIMARY KEY (technician_id) NOT ENFORCED
) WITH (
    'connector' = 'jdbc',
    'url' = 'jdbc:postgresql://db:5432/${POSTGRES_DB}',
    'table-name' = 'analytics.intervention_technician_load',
    'username' = '${POSTGRES_USER}',
    'password' = '${POSTGRES_PASSWORD}',
    'driver' = 'org.postgresql.Driver',
    'sink.buffer-flush.interval' = '5 s',
    'sink.buffer-flush.max-rows' = '1000'
);

CREATE TABLE IF NOT EXISTS analytics_intervention_geo_view (
    intervention_id BIGINT,
    latitude DOUBLE,
    longitude DOUBLE,
    status STRING,
    technician_id BIGINT,
    planned_at TIMESTAMP_LTZ(3),
    updated_at TIMESTAMP_LTZ(3),
    PRIMARY KEY (intervention_id) NOT ENFORCED
) WITH (
    'connector' = 'jdbc',
    'url' = 'jdbc:postgresql://db:5432/${POSTGRES_DB}',
    'table-name' = 'analytics.intervention_geo_view',
    'username' = '${POSTGRES_USER}',
    'password' = '${POSTGRES_PASSWORD}',
    'driver' = 'org.postgresql.Driver',
    'sink.buffer-flush.interval' = '5 s',
    'sink.buffer-flush.max-rows' = '1000'
);

CREATE TEMPORARY VIEW daily_status_counts AS
SELECT
    CAST(planned_at AS DATE) AS metric_date,
    status,
    COUNT(*) AS total_count,
    AVG(
        CASE
            WHEN completed_at IS NOT NULL AND started_at IS NOT NULL
                THEN TIMESTAMPDIFF(SECOND, started_at, completed_at)
            ELSE NULL
        END
    ) AS avg_completion_seconds,
    MAX(source_ts_ms) AS last_event_ts
FROM intervention_events
WHERE op IS NULL OR op <> 'd'
GROUP BY CAST(planned_at AS DATE), status;

CREATE TEMPORARY VIEW daily_completion_summary AS
SELECT
    metric_date,
    SUM(CASE WHEN status IN ('COMPLETED', 'VALIDATED') THEN total_count ELSE 0 END) AS completed_total,
    SUM(CASE WHEN status = 'VALIDATED' THEN total_count ELSE 0 END) AS validated_total,
    MAX(last_event_ts) AS last_event_ts
FROM daily_status_counts
GROUP BY metric_date;

INSERT INTO analytics_intervention_daily_metrics
SELECT
    c.metric_date,
    c.status,
    c.total_count,
    c.avg_completion_seconds,
    CASE
        WHEN c.status = 'VALIDATED' AND summary.completed_total > 0
            THEN (summary.validated_total * 100.0) / summary.completed_total
        ELSE NULL
    END AS validation_ratio,
    COALESCE(summary.last_event_ts, CURRENT_TIMESTAMP) AS last_refreshed_at
FROM daily_status_counts c
LEFT JOIN daily_completion_summary summary ON summary.metric_date = c.metric_date;

INSERT INTO analytics_intervention_technician_load
SELECT
    technician_id,
    SUM(CASE WHEN status IN ('SCHEDULED', 'IN_PROGRESS') THEN 1 ELSE 0 END) AS open_count,
    SUM(
        CASE
            WHEN status IN ('COMPLETED', 'VALIDATED')
                 AND CAST(completed_at AS DATE) = CURRENT_DATE
            THEN 1 ELSE 0
        END
    ) AS completed_today,
    AVG(
        CASE
            WHEN completed_at IS NOT NULL AND started_at IS NOT NULL
                THEN TIMESTAMPDIFF(SECOND, started_at, completed_at)
            ELSE NULL
        END
    ) AS avg_completion_seconds,
    MAX(source_ts_ms) AS last_refreshed_at
FROM intervention_events
WHERE technician_id IS NOT NULL AND (op IS NULL OR op <> 'd')
GROUP BY technician_id;

INSERT INTO analytics_intervention_geo_view
SELECT
    id AS intervention_id,
    latitude,
    longitude,
    status,
    technician_id,
    planned_at,
    COALESCE(updated_at, CURRENT_TIMESTAMP) AS updated_at
FROM intervention_events
WHERE (op IS NULL OR op <> 'd') AND latitude IS NOT NULL AND longitude IS NOT NULL;
