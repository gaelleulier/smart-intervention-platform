"""Flink streaming job aggregating intervention events into analytics tables."""

from __future__ import annotations

import os
from pyflink.table import EnvironmentSettings, TableEnvironment


KAFKA_BOOTSTRAP = os.environ.get("KAFKA_BOOTSTRAP", "kafka:9092")
POSTGRES_HOST = os.environ.get("POSTGRES_HOST", "db")
POSTGRES_PORT = os.environ.get("POSTGRES_PORT", "5432")
POSTGRES_DB = os.environ.get("POSTGRES_DB", "sip_db")
POSTGRES_USER = os.environ.get("POSTGRES_USER", "sip_user")
POSTGRES_PASSWORD = os.environ.get("POSTGRES_PASSWORD", "sip_password")
INTERVENTION_TOPIC = os.environ.get("CDC_TOPIC", "sip.interventions")


def create_table_env() -> TableEnvironment:
    settings = EnvironmentSettings.in_streaming_mode()
    t_env = TableEnvironment.create(settings)
    t_env.get_config().get_configuration().set_string("table.exec.source.idle-timeout", "5 s")
    t_env.get_config().get_configuration().set_string("table.exec.state.ttl", "1 h")
    return t_env


def register_sources(t_env: TableEnvironment) -> None:
    t_env.execute_sql(
        f"""
        CREATE TABLE intervention_events (
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
            'topic' = '{INTERVENTION_TOPIC}',
            'properties.bootstrap.servers' = '{KAFKA_BOOTSTRAP}',
            'scan.startup.mode' = 'latest-offset',
            'value.format' = 'json',
            'value.json.fail-on-missing-field' = 'false',
            'value.fields-include' = 'EXCEPT_KEY',
            'key.format' = 'json',
            'key.json.fail-on-missing-field' = 'false',
            'key.fields-include' = 'NONE'
        )
        """
    )


def register_sinks(t_env: TableEnvironment) -> None:
    jdbc_common_options = f"""
        'connector' = 'jdbc',
        'url' = 'jdbc:postgresql://{POSTGRES_HOST}:{POSTGRES_PORT}/{POSTGRES_DB}',
        'username' = '{POSTGRES_USER}',
        'password' = '{POSTGRES_PASSWORD}',
        'driver' = 'org.postgresql.Driver'
    """

    t_env.execute_sql(
        f"""
        CREATE TABLE analytics_intervention_daily_metrics (
            metric_date DATE,
            status STRING,
            total_count BIGINT,
            avg_completion_seconds DOUBLE,
            validation_ratio DOUBLE,
            last_refreshed_at TIMESTAMP_LTZ(3),
            PRIMARY KEY (metric_date, status) NOT ENFORCED
        ) WITH (
            {jdbc_common_options},
            'table-name' = 'analytics.intervention_daily_metrics'
        )
        """
    )

    t_env.execute_sql(
        f"""
        CREATE TABLE analytics_intervention_technician_load (
            technician_id BIGINT,
            open_count BIGINT,
            completed_today BIGINT,
            avg_completion_seconds DOUBLE,
            last_refreshed_at TIMESTAMP_LTZ(3),
            PRIMARY KEY (technician_id) NOT ENFORCED
        ) WITH (
            {jdbc_common_options},
            'table-name' = 'analytics.intervention_technician_load'
        )
        """
    )

    t_env.execute_sql(
        f"""
        CREATE TABLE analytics_intervention_geo_view (
            intervention_id BIGINT,
            latitude DOUBLE,
            longitude DOUBLE,
            status STRING,
            technician_id BIGINT,
            planned_at TIMESTAMP_LTZ(3),
            updated_at TIMESTAMP_LTZ(3),
            PRIMARY KEY (intervention_id) NOT ENFORCED
        ) WITH (
            {jdbc_common_options},
            'table-name' = 'analytics.intervention_geo_view'
        )
        """
    )


def create_materialized_streams(t_env: TableEnvironment) -> None:
    t_env.execute_sql(
        """
        CREATE TEMPORARY VIEW daily_status_counts AS
        SELECT
            CAST(planned_at AT TIME ZONE 'UTC' AS DATE) AS metric_date,
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
        GROUP BY CAST(planned_at AT TIME ZONE 'UTC' AS DATE), status
        """
    )

    t_env.execute_sql(
        """
        CREATE TEMPORARY VIEW daily_completion_summary AS
        SELECT
            metric_date,
            SUM(CASE WHEN status IN ('COMPLETED', 'VALIDATED') THEN total_count ELSE 0 END) AS completed_total,
            SUM(CASE WHEN status = 'VALIDATED' THEN total_count ELSE 0 END) AS validated_total,
            MAX(last_event_ts) AS last_event_ts
        FROM daily_status_counts
        GROUP BY metric_date
        """
    )

    t_env.execute_sql(
        """
        INSERT INTO analytics_intervention_daily_metrics
        SELECT
            d.metric_date,
            d.status,
            d.total_count,
            d.avg_completion_seconds,
            CASE
                WHEN d.status = 'VALIDATED' AND summary.completed_total > 0 THEN
                    (summary.validated_total * 100.0) / summary.completed_total
                ELSE NULL
            END AS validation_ratio,
            COALESCE(summary.last_event_ts, CURRENT_TIMESTAMP) AS last_refreshed_at
        FROM daily_status_counts d
        LEFT JOIN daily_completion_summary summary ON summary.metric_date = d.metric_date
        """
    )

    t_env.execute_sql(
        """
        CREATE TEMPORARY VIEW technician_stats AS
        SELECT
            technician_id,
            SUM(CASE WHEN status IN ('SCHEDULED', 'IN_PROGRESS') THEN 1 ELSE 0 END) AS open_count,
            SUM(
                CASE
                    WHEN status IN ('COMPLETED', 'VALIDATED')
                         AND completed_at >= DATE_TRUNC('DAY', CURRENT_TIMESTAMP)
                         AND completed_at < DATE_TRUNC('DAY', CURRENT_TIMESTAMP) + INTERVAL '1' DAY
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
            MAX(source_ts_ms) AS last_event_ts
        FROM intervention_events
        WHERE technician_id IS NOT NULL AND (op IS NULL OR op <> 'd')
        GROUP BY technician_id
        """
    )

    t_env.execute_sql(
        """
        INSERT INTO analytics_intervention_technician_load
        SELECT
            technician_id,
            open_count,
            completed_today,
            avg_completion_seconds,
            COALESCE(last_event_ts, CURRENT_TIMESTAMP) AS last_refreshed_at
        FROM technician_stats
        """
    )

    t_env.execute_sql(
        """
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
        WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND (op IS NULL OR op <> 'd')
        """
    )


def main() -> None:
    t_env = create_table_env()
    register_sources(t_env)
    register_sinks(t_env)
    create_materialized_streams(t_env)
    # Block so the streaming job keeps running.
    t_env.execute("intervention-metrics-job")


if __name__ == "__main__":
    main()
