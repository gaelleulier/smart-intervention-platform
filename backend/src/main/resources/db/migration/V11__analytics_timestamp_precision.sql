ALTER TABLE analytics.intervention_daily_metrics
    ALTER COLUMN last_refreshed_at TYPE TIMESTAMP WITHOUT TIME ZONE USING last_refreshed_at AT TIME ZONE 'UTC',
    ALTER COLUMN last_refreshed_at SET DEFAULT (now() AT TIME ZONE 'UTC');

ALTER TABLE analytics.intervention_technician_load
    ALTER COLUMN last_refreshed_at TYPE TIMESTAMP WITHOUT TIME ZONE USING last_refreshed_at AT TIME ZONE 'UTC',
    ALTER COLUMN last_refreshed_at SET DEFAULT (now() AT TIME ZONE 'UTC');

ALTER TABLE analytics.intervention_geo_view
    ALTER COLUMN planned_at TYPE TIMESTAMP WITHOUT TIME ZONE USING planned_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITHOUT TIME ZONE USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'UTC');
