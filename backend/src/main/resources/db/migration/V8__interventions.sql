CREATE TABLE IF NOT EXISTS interventions (
    id BIGSERIAL PRIMARY KEY,
    reference VARCHAR(50) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    assignment_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    planned_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    validated_at TIMESTAMPTZ,
    technician_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_interventions_reference_ci ON interventions ((lower(reference)));
CREATE INDEX IF NOT EXISTS idx_interventions_status ON interventions (status);
CREATE INDEX IF NOT EXISTS idx_interventions_planned_at ON interventions (planned_at);

ALTER TABLE interventions
    ADD CONSTRAINT chk_interventions_status CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'VALIDATED'));

ALTER TABLE interventions
    ADD CONSTRAINT chk_interventions_assignment_mode CHECK (assignment_mode IN ('AUTO', 'MANUAL'));

ALTER TABLE interventions
    ADD CONSTRAINT fk_interventions_technician
    FOREIGN KEY (technician_id)
    REFERENCES users (id);
