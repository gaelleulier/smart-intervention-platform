-- Seed rich demo data focused around Toulouse for analytics showcases.
DO
$$
DECLARE
    default_hash CONSTANT TEXT := '$2b$10$AeS3LwKeoIoAMIB2q0N7Q.xSon5w7ltIoHCVWjEOlZ7IDIggSKRSy';
BEGIN
    INSERT INTO users (email, full_name, role, password_hash)
    VALUES
        ('lucie.fabre@sip.local',      'Lucie Fabre',      'DISPATCHER', default_hash),
        ('pierre.leroy@sip.local',     'Pierre Leroy',     'DISPATCHER', default_hash),
        ('alexandre.martin@sip.local', 'Alexandre Martin', 'TECH',       default_hash),
        ('lea.bernard@sip.local',      'Lea Bernard',      'TECH',       default_hash),
        ('yacine.benali@sip.local',    'Yacine Benali',    'TECH',       default_hash),
        ('sophia.renard@sip.local',    'Sophia Renard',    'TECH',       default_hash),
        ('nicolas.petit@sip.local',    'Nicolas Petit',    'TECH',       default_hash)
    ON CONFLICT ((lower(email))) DO UPDATE
        SET full_name = EXCLUDED.full_name,
            role = EXCLUDED.role,
            password_hash = default_hash;

    WITH demo_interventions AS (
        SELECT *
        FROM (VALUES
            (
                'TLS-2025-001',
                'Inspection reseau Capitole',
                'Controle de la fibre optique autour de la place du Capitole.',
                'COMPLETED',
                'MANUAL',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '7 days') + INTERVAL '07 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '7 days') + INTERVAL '07 hours 15 minutes',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '7 days') + INTERVAL '09 hours',
                NULL,
                'alexandre.martin@sip.local',
                43.604500::numeric(9,6),
                1.444000::numeric(9,6),
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '7 days') - INTERVAL '6 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '7 days') + INTERVAL '09 hours'
            ),
            (
                'TLS-2025-002',
                'Validation maintenance Hotel-Dieu',
                'Validation finale du groupe froid apres reparation.',
                'VALIDATED',
                'AUTO',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '7 days') + INTERVAL '10 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '7 days') + INTERVAL '10 hours 10 minutes',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '7 days') + INTERVAL '11 hours 20 minutes',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '7 days') + INTERVAL '11 hours 50 minutes',
                'alexandre.martin@sip.local',
                43.602900::numeric(9,6),
                1.438500::numeric(9,6),
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '7 days') - INTERVAL '4 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '7 days') + INTERVAL '12 hours'
            ),
            (
                'TLS-2025-003',
                'Remise en service Blagnac',
                'Redemarrage des equipements d''arrosage automatique.',
                'COMPLETED',
                'MANUAL',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '6 days') + INTERVAL '08 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '6 days') + INTERVAL '08 hours 20 minutes',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '6 days') + INTERVAL '10 hours',
                NULL,
                'lea.bernard@sip.local',
                43.635200::numeric(9,6),
                1.407800::numeric(9,6),
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '6 days') - INTERVAL '8 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '6 days') + INTERVAL '10 hours'
            ),
            (
                'TLS-2025-004',
                'Audit eclairage Purpan',
                'Verification energetique du centre hospitalier Purpan.',
                'VALIDATED',
                'AUTO',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '6 days') + INTERVAL '13 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '6 days') + INTERVAL '13 hours 15 minutes',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '6 days') + INTERVAL '14 hours 40 minutes',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '6 days') + INTERVAL '15 hours',
                'lea.bernard@sip.local',
                43.611800::numeric(9,6),
                1.425500::numeric(9,6),
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '6 days') - INTERVAL '3 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '6 days') + INTERVAL '15 hours 15 minutes'
            ),
            (
                'TLS-2025-005',
                'Assistance capteurs IoT Jolimont',
                'Remplacement des capteurs meteo IoT defectueux.',
                'IN_PROGRESS',
                'MANUAL',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '2 days') + INTERVAL '08 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '2 days') + INTERVAL '08 hours 10 minutes',
                NULL,
                NULL,
                'yacine.benali@sip.local',
                43.613900::numeric(9,6),
                1.467200::numeric(9,6),
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '2 days') - INTERVAL '5 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '2 days') + INTERVAL '12 hours'
            ),
            (
                'TLS-2025-006',
                'Preparation intervention Carmes',
                'Preparation d''une coupure reseau planifiee rue des Filatiers.',
                'SCHEDULED',
                'AUTO',
                date_trunc('day', CURRENT_TIMESTAMP + INTERVAL '1 day') + INTERVAL '09 hours 30 minutes',
                NULL,
                NULL,
                NULL,
                'sophia.renard@sip.local',
                43.596700::numeric(9,6),
                1.442600::numeric(9,6),
                date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '2 hours',
                date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '1 day'
            ),
            (
                'TLS-2025-007',
                'Diagnostic borne electrique Cote Pavee',
                'Diagnostic rapide sur une borne de recharge vehicules.',
                'IN_PROGRESS',
                'MANUAL',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '1 day') + INTERVAL '10 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '1 day') + INTERVAL '10 hours 30 minutes',
                NULL,
                NULL,
                'nicolas.petit@sip.local',
                43.593800::numeric(9,6),
                1.476400::numeric(9,6),
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '1 day') - INTERVAL '4 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '1 day') + INTERVAL '12 hours'
            ),
            (
                'TLS-2025-008',
                'Inspection toiture Labege',
                'Inspection thermique d''un entrepot logistique.',
                'COMPLETED',
                'AUTO',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '5 days') + INTERVAL '11 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '5 days') + INTERVAL '11 hours 10 minutes',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '5 days') + INTERVAL '12 hours 20 minutes',
                NULL,
                'alexandre.martin@sip.local',
                43.533900::numeric(9,6),
                1.511200::numeric(9,6),
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '5 days') - INTERVAL '6 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '5 days') + INTERVAL '12 hours 30 minutes'
            ),
            (
                'TLS-2025-009',
                'Controle securite aeronautique',
                'Validation securite sur hangar auxiliaire de l''aeroport.',
                'VALIDATED',
                'MANUAL',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '4 days') + INTERVAL '08 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '4 days') + INTERVAL '08 hours 25 minutes',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '4 days') + INTERVAL '10 hours 05 minutes',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '4 days') + INTERVAL '10 hours 45 minutes',
                'yacine.benali@sip.local',
                43.629400::numeric(9,6),
                1.364900::numeric(9,6),
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '4 days') - INTERVAL '2 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '4 days') + INTERVAL '11 hours'
            ),
            (
                'TLS-2025-010',
                'Remplacement capteurs Arenes',
                'Remplacement express des capteurs de pollution.',
                'COMPLETED',
                'MANUAL',
                date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '07 hours 30 minutes',
                date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '07 hours 45 minutes',
                date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '09 hours 15 minutes',
                NULL,
                'sophia.renard@sip.local',
                43.588600::numeric(9,6),
                1.419200::numeric(9,6),
                date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '3 hours',
                date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '09 hours 20 minutes'
            ),
            (
                'TLS-2025-011',
                'Planification data center Basso Cambo',
                'Planification d''un arret sur les onduleurs redondes.',
                'SCHEDULED',
                'AUTO',
                date_trunc('day', CURRENT_TIMESTAMP + INTERVAL '2 days') + INTERVAL '14 hours',
                NULL,
                NULL,
                NULL,
                'lea.bernard@sip.local',
                43.572400::numeric(9,6),
                1.402100::numeric(9,6),
                date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '1 hour',
                date_trunc('day', CURRENT_TIMESTAMP + INTERVAL '2 days') + INTERVAL '01 hours'
            ),
            (
                'TLS-2025-012',
                'Monitoring digue Garonne',
                'Installation de capteurs de vibration le long de la digue.',
                'COMPLETED',
                'MANUAL',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '2 days') + INTERVAL '12 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '2 days') + INTERVAL '12 hours 15 minutes',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '2 days') + INTERVAL '14 hours',
                NULL,
                'nicolas.petit@sip.local',
                43.600800::numeric(9,6),
                1.436300::numeric(9,6),
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '2 days') - INTERVAL '4 hours',
                date_trunc('day', CURRENT_TIMESTAMP - INTERVAL '2 days') + INTERVAL '14 hours 30 minutes'
            )
        ) AS t(
            reference,
            title,
            description,
            status,
            assignment_mode,
            planned_at,
            started_at,
            completed_at,
            validated_at,
            technician_email,
            latitude,
            longitude,
            created_at,
            updated_at
        )
    )
    INSERT INTO interventions (
        reference,
        title,
        description,
        status,
        assignment_mode,
        planned_at,
        started_at,
        completed_at,
        validated_at,
        technician_id,
        latitude,
        longitude,
        created_at,
        updated_at
    )
    SELECT
        d.reference,
        d.title,
        d.description,
        d.status,
        d.assignment_mode,
        d.planned_at,
        d.started_at,
        d.completed_at,
        d.validated_at,
        (SELECT id FROM users WHERE email = d.technician_email),
        d.latitude,
        d.longitude,
        d.created_at,
        d.updated_at
    FROM demo_interventions d
    ON CONFLICT ((lower(reference))) DO UPDATE
        SET title = EXCLUDED.title,
            description = EXCLUDED.description,
            status = EXCLUDED.status,
            assignment_mode = EXCLUDED.assignment_mode,
            planned_at = EXCLUDED.planned_at,
            started_at = EXCLUDED.started_at,
            completed_at = EXCLUDED.completed_at,
            validated_at = EXCLUDED.validated_at,
            technician_id = EXCLUDED.technician_id,
            latitude = EXCLUDED.latitude,
            longitude = EXCLUDED.longitude,
            updated_at = EXCLUDED.updated_at;

    WITH new_refs(ref) AS (
        VALUES
            ('TLS-2025-001'),
            ('TLS-2025-002'),
            ('TLS-2025-003'),
            ('TLS-2025-004'),
            ('TLS-2025-005'),
            ('TLS-2025-006'),
            ('TLS-2025-007'),
            ('TLS-2025-008'),
            ('TLS-2025-009'),
            ('TLS-2025-010'),
            ('TLS-2025-011'),
            ('TLS-2025-012')
    ),
    base AS (
        SELECT
            i.*,
            (COALESCE(i.planned_at, i.started_at, i.created_at))::date AS metric_date
        FROM interventions i
        JOIN new_refs r ON r.ref = i.reference
    ),
    aggregated AS (
        SELECT
            metric_date,
            status,
            COUNT(*) AS total_count,
            AVG(
                CASE
                    WHEN completed_at IS NOT NULL
                         AND started_at IS NOT NULL
                    THEN EXTRACT(EPOCH FROM completed_at - started_at)
                    ELSE NULL
                END
            ) AS avg_completion_seconds,
            MAX(updated_at) AS last_refreshed_at
        FROM base
        GROUP BY metric_date, status
    ),
    completion_summary AS (
        SELECT
            metric_date,
            SUM(CASE WHEN status IN ('COMPLETED','VALIDATED') THEN total_count ELSE 0 END) AS completed_total,
            SUM(CASE WHEN status = 'VALIDATED' THEN total_count ELSE 0 END) AS validated_total
        FROM aggregated
        GROUP BY metric_date
    )
    INSERT INTO analytics.intervention_daily_metrics (
        metric_date,
        status,
        total_count,
        avg_completion_seconds,
        validation_ratio,
        last_refreshed_at
    )
    SELECT
        a.metric_date,
        a.status,
        a.total_count,
        CASE WHEN a.status = 'COMPLETED' THEN a.avg_completion_seconds ELSE NULL END,
        CASE
            WHEN a.status = 'VALIDATED' AND completion_summary.completed_total > 0
                THEN (completion_summary.validated_total::numeric / completion_summary.completed_total::numeric) * 100
            ELSE NULL
        END,
        a.last_refreshed_at
    FROM aggregated a
    JOIN completion_summary ON completion_summary.metric_date = a.metric_date
    ON CONFLICT (metric_date, status) DO UPDATE
        SET total_count = EXCLUDED.total_count,
            avg_completion_seconds = EXCLUDED.avg_completion_seconds,
            validation_ratio = EXCLUDED.validation_ratio,
            last_refreshed_at = EXCLUDED.last_refreshed_at;

    WITH new_refs(ref) AS (
        VALUES
            ('TLS-2025-001'),
            ('TLS-2025-002'),
            ('TLS-2025-003'),
            ('TLS-2025-004'),
            ('TLS-2025-005'),
            ('TLS-2025-006'),
            ('TLS-2025-007'),
            ('TLS-2025-008'),
            ('TLS-2025-009'),
            ('TLS-2025-010'),
            ('TLS-2025-011'),
            ('TLS-2025-012')
    ),
    per_tech AS (
        SELECT
            technician_id,
            SUM(CASE WHEN status IN ('SCHEDULED','IN_PROGRESS') THEN 1 ELSE 0 END) AS open_count,
            SUM(
                CASE
                    WHEN status IN ('COMPLETED','VALIDATED')
                         AND completed_at IS NOT NULL
                         AND completed_at::date = CURRENT_DATE
                    THEN 1
                    ELSE 0
                END
            ) AS completed_today,
            AVG(
                CASE
                    WHEN completed_at IS NOT NULL AND started_at IS NOT NULL
                    THEN EXTRACT(EPOCH FROM completed_at - started_at)
                    ELSE NULL
                END
            ) AS avg_completion_seconds,
            MAX(updated_at) AS last_refreshed_at
        FROM interventions i
        JOIN new_refs r ON r.ref = i.reference
        WHERE technician_id IS NOT NULL
        GROUP BY technician_id
    )
    INSERT INTO analytics.intervention_technician_load (
        technician_id,
        open_count,
        completed_today,
        avg_completion_seconds,
        last_refreshed_at
    )
    SELECT
        p.technician_id,
        p.open_count,
        p.completed_today,
        p.avg_completion_seconds,
        p.last_refreshed_at
    FROM per_tech p
    ON CONFLICT (technician_id) DO UPDATE
        SET open_count = EXCLUDED.open_count,
            completed_today = EXCLUDED.completed_today,
            avg_completion_seconds = EXCLUDED.avg_completion_seconds,
            last_refreshed_at = EXCLUDED.last_refreshed_at;

    WITH new_refs(ref) AS (
        VALUES
            ('TLS-2025-001'),
            ('TLS-2025-002'),
            ('TLS-2025-003'),
            ('TLS-2025-004'),
            ('TLS-2025-005'),
            ('TLS-2025-006'),
            ('TLS-2025-007'),
            ('TLS-2025-008'),
            ('TLS-2025-009'),
            ('TLS-2025-010'),
            ('TLS-2025-011'),
            ('TLS-2025-012')
    )
    INSERT INTO analytics.intervention_geo_view (
        intervention_id,
        latitude,
        longitude,
        status,
        technician_id,
        planned_at,
        updated_at
    )
    SELECT
        i.id,
        i.latitude,
        i.longitude,
        i.status,
        i.technician_id,
        i.planned_at,
        i.updated_at
    FROM interventions i
    JOIN new_refs r ON r.ref = i.reference
    ON CONFLICT (intervention_id) DO UPDATE
        SET latitude = EXCLUDED.latitude,
            longitude = EXCLUDED.longitude,
            status = EXCLUDED.status,
            technician_id = EXCLUDED.technician_id,
            planned_at = EXCLUDED.planned_at,
            updated_at = EXCLUDED.updated_at;
END;
$$;
