CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE IF NOT EXISTS analytics.intervention_daily_metrics (
    metric_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_count BIGINT NOT NULL DEFAULT 0,
    avg_completion_seconds NUMERIC(12, 2),
    validation_ratio NUMERIC(5, 2),
    last_refreshed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_intervention_daily_metrics PRIMARY KEY (metric_date, status),
    CONSTRAINT chk_intervention_daily_metrics_status CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'VALIDATED'))
);

CREATE INDEX IF NOT EXISTS idx_intervention_daily_metrics_date ON analytics.intervention_daily_metrics (metric_date);

CREATE TABLE IF NOT EXISTS analytics.intervention_technician_load (
    technician_id BIGINT NOT NULL,
    open_count BIGINT NOT NULL DEFAULT 0,
    completed_today BIGINT NOT NULL DEFAULT 0,
    avg_completion_seconds NUMERIC(12, 2),
    last_refreshed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_intervention_technician_load PRIMARY KEY (technician_id),
    CONSTRAINT fk_intervention_technician_load_technician FOREIGN KEY (technician_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS analytics.intervention_geo_view (
    intervention_id BIGINT PRIMARY KEY,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    technician_id BIGINT,
    planned_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_intervention_geo_view_intervention FOREIGN KEY (intervention_id) REFERENCES interventions (id) ON DELETE CASCADE,
    CONSTRAINT fk_intervention_geo_view_technician FOREIGN KEY (technician_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_intervention_geo_view_status CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'VALIDATED'))
);

CREATE INDEX IF NOT EXISTS idx_intervention_geo_view_status ON analytics.intervention_geo_view (status);
CREATE INDEX IF NOT EXISTS idx_intervention_geo_view_updated_at ON analytics.intervention_geo_view (updated_at DESC);
