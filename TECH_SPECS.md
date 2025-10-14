# Smart Intervention Platform – Technical Specifications

This document defines the architectural guardrails for the Smart Intervention Platform monorepo. Every change affecting the API, database, build pipeline, or runtime architecture must be reflected here prior to implementation.

## 1. System Overview
- **Backend**: Spring Boot 3.5.x (Java 21), Maven build. Exposes stateless REST APIs under the `/api` context path.
- **Frontend**: Angular 18 SPA (TypeScript) served via Angular dev server in development and via static assets in production.
- **Database**: PostgreSQL 16, managed through Flyway migrations.
- **Infrastructure**: Docker Compose orchestrates the dev stack. Production containerization is planned but not finalized.
- **Configuration**: Application settings come from `application-*.yml` files and overridden by environment variables. Secrets must never be committed.

## 2. Domain & Modules
- MVP scope focuses on **Users Management** (authentication, authorization, CRUD).
- **Intervention Management** module manages creation, scheduling, and lifecycle of field operations with
  automatic/manual technician assignment and state transitions (`SCHEDULED → IN_PROGRESS → COMPLETED → VALIDATED`).
- **Dashboard & Reporting** module aggregates operational KPIs, exposes map visualizations, and consumes analytics pipelines fed from intervention events.
- Future modules must be scoped as dedicated bounded contexts, following clear API versioning when contracts change.

## 3. Backend Guidelines
- Package structure mirrors functional domains: `io.smartip.<domain>`.
- **API Design**:
  - RESTful endpoints, JSON bodies, RFC 7807 problem details for errors.
  - Enforce validation using Jakarta Validation (`@Valid`) and custom constraint annotations when required.
  - Pagination uses Spring Data conventions (`page`, `size`, `sort`).
  - Interventions API published under `/api/interventions` with filters (`query`, `status`, `assignmentMode`, `technicianId`, `plannedFrom`, `plannedTo`). Only admins/dispatchers can create or edit; technicians may progress the status of their own assignments.
- **Persistence**:
  - JPA entities mapped via Hibernate; prefer explicit column definitions for clarity.
  - Use repositories for data access and services for business logic. Keep controllers thin.
- **Transactions**: Declare `@Transactional` at service level; avoid transactions in controllers.
- **Security**:
  - Stateless JWT authentication with signing secret provided via `JWT_SECRET`.
  - Authorization managed through Spring Security with role-based access controls.
  - Sensitive logs (passwords, tokens) must never be printed.
- **Configuration profiles**:
  - `dev`: connects to Dockerized Postgres, enables verbose logging where helpful.
  - `prod`: targets managed Postgres, uses secure defaults, and disables debug endpoints.

## 4. Frontend Guidelines
- Structure features under `src/app/<feature>` with a dedicated routing module when routes are exposed.
- Services interact with backend through Angular `HttpClient`. Shared API models live under `src/app/shared/models`.
- Use RxJS best practices (prefer `Observable` streams, avoid nested subscriptions).
- Styling with SCSS; adopt BEM naming for global styles. Component styles should remain encapsulated.
- Forms leverage Angular Reactive Forms with validators mirroring backend rules.

## 5. Database & Migrations
- All schema changes must go through Flyway scripts located at `backend/src/main/resources/db/migration` and follow the `V{version}__{description}.sql` naming scheme.
- Never modify an applied migration; create a follow-up migration to adjust data or schema.
- Required extensions: `uuid-ossp` (UUID generation) and `pgcrypto` (cryptographic helpers).
- Seed data for local development must be clearly segregated and idempotent.
- Interventions persist optional geolocation metadata (`latitude`, `longitude`) captured from the UI and replicated into analytics views.

## 6. Testing Strategy
- **Backend**: JUnit 5 for unit/integration tests, leveraging Spring Boot Test slices when possible. Security-sensitive endpoints require dedicated tests.
- **Frontend**: Jasmine/Karma unit tests, Cypress (planned) for e2e once the UI stabilizes.
- **CI**: All automated tests must run in GitLab pipelines before merge.

## 7. Observability
- Spring Boot Actuator enabled with `/actuator/health`. Additional indicators should be exposed for critical dependencies (DB, external services).
- Application logs follow JSON format in production (TBD). Use structured logging for correlation IDs when integrating with external systems.

## 8. Documentation Requirements
- README.md covers onboarding. Significant architectural decisions belong in this file.
- Every feature or fix must update this document if it alters APIs, database schema, CI/CD, or architecture.

## 9. Branching & Workflow
- Use feature branches named after the change scope (e.g., `feat/users-crud`, `fix/auth-refresh-token`).
- Commits follow Conventional Commits. Intermediate checkpoints use `chore(savepoint): ...`.
- Do not merge to main without passing CI and code review.

## 10. Pending Decisions / TODOs
- Define production containerization strategy (Dockerfiles, orchestration).
- Introduce comprehensive CI pipeline stages (lint, test, build, publish).
- Finalize logging/monitoring stack for production (e.g., ELK, OpenTelemetry).

## 11. Dashboard & Reporting Module
- **Objectives**:
  - Provide real-time-ish operational KPIs (daily intervention volume, status distribution, technician workload, SLA adherence).
  - Offer geo-visualization of interventions (planned vs. active) on an interactive map.
  - Serve analytics-ready aggregates produced by the intervention pipeline (stream/batch) without impacting OLTP workloads.

- **Backend Architecture**:
  - Package namespace: `io.smartip.dashboard`.
  - REST controller under `/api/dashboard` exposing:
    - `GET /summary`: totals for current day/week, average completion time, validation ratio.
    - `GET /status-trends`: time-series grouped per day/week with status buckets.
    - `GET /technician-load`: open vs. completed counts per technician, ordered by load.
    - `GET /map`: geo-referenced interventions with status and assignment metadata.
  - DTOs returned in lightweight numeric formats (no entities). Use records under `io.smartip.dashboard.dto`.
  - Service layer consumes pre-aggregated tables or materialized views; fallback to dynamic aggregation only when data volume < 10k rows.
  - Repository layer targets analytics schema: use dedicated Spring Data projections (`@Query(nativeQuery = true)` or `JdbcTemplate`) to avoid JPA entity inflation.
  - Responses cached via Spring Cache (`@Cacheable`) with configurable TTL (default 60 seconds) to cap load when dashboards auto-refresh.
  - Security: endpoints restricted to `ADMIN` and `DISPATCHER`; technicians receive read-only `GET /technician-load` filtered on their assignments. Apply method-level guards (`@PreAuthorize`).

- **Frontend Architecture**:
  - New feature folder `src/app/dashboard` with routes `/dashboard` (default redirect from `/` once module GA).
  - Components:
    - `DashboardPageComponent`: orchestrates data fetch, error handling, refresh cadence.
    - `KpiCardsComponent`, `StatusTrendChartComponent`, `TechnicianLoadComponent`, `InterventionMapComponent`.
  - State handled via RxJS signals or component store; keep HTTP services in `dashboard.service.ts`.
  - Visualization libraries:
    - Charts: leverage Angular + `ngx-charts` (or D3 wrapper) for bar/line charts.
    - Map: default to Leaflet via `@asymmetrik/ngx-leaflet`; Google Maps optional behind feature flag if customers require satellite view. Leaflet tiles use OpenStreetMap with configurable tile URL and API key support.
  - Responsive layout (desktop: multi-column grid; mobile: stacked cards). Ensure high-contrast color palette for statuses.

- **Analytics Pipeline**:
  - Change Data Capture from `interventions` table via Debezium connector (Postgres slot) -> Kafka topic `sip.interventions` with payload flattened through `ExtractNewRecordState`.
  - Local developer stack ships Zookeeper, Kafka, Debezium Connect, Flink (job/task manager) and Kafka UI (`docker-compose.dev.yml`). Kafka Connect configs live under `infra/cdc/connectors` and are registered via `scripts/register-connectors.sh`.
  - Stream processing is handled by a Flink SQL job template (`infra/cdc/flink/analytics_job.sql`) submitted via `scripts/submit-flink-job.sh`. The job performs upserts into PostgreSQL analytics tables (`analytics.intervention_daily_metrics`, `analytics.intervention_technician_load`, `analytics.intervention_geo_view`).
  - Nightly batch (optional) replays aggregates to correct drift; orchestrated via Airflow using the same processing DAG. Spring fallback `/api/dashboard/refresh` delegates to `AnalyticsAggregationService` (disabled by default) for manual recompaction.
  - Schema:
    - `analytics.intervention_daily_metrics`: columns (`date`, `status`, `count`, `avg_completion_seconds`, `validation_ratio`).
    - `analytics.intervention_technician_load`: (`technician_id`, `open_count`, `completed_today`, `avg_completion_seconds`).
    - `analytics.intervention_geo_view`: (`intervention_id`, `latitude`, `longitude`, `status`, `technician_id`, `planned_at`, `updated_at`).
  - Materialized views refreshed continuously by the Flink job; expose topic/table mapping in `infra/cdc/README.md` for ops visibility.
  - `AnalyticsAggregationService` (Spring) remains as an on-demand fallback (`dashboard.analytics.refresh-enabled=false` by default) and powers the `/api/dashboard/refresh` endpoint.

- **Data Quality & Governance**:
  - Enforce presence of geolocation metadata when scheduling interventions (validation on backend & Flyway NOT NULL columns once adoption validated).
  - Add anomaly detection job (e.g., threshold alerts when validation ratio drops below 80%) publishing to monitoring stack (future work).
  - Document lineage in TECH_SPECS.md and maintain data contracts between OLTP events and analytics schema.

- **Testing & Performance**:
  - Backend: contract tests for `/api/dashboard` ensuring cached responses and role guards; integration tests hitting analytics schema fixtures.
  - Frontend: component/unit tests for chart formatting, map markers, and data refresh logic.
  - Pipeline: include replayable integration tests (Docker Compose profile) validating CDC -> stream aggregator -> analytics tables.
  - SLAs: Summary endpoint p95 < 200 ms (served from cached view); map payload limited to 500 markers per request (paginate beyond).

- **Security & Compliance**:
  - Data served from analytics schema must avoid PII beyond technician full name; anonymize customer data before ingestion.
  - Ensure map endpoints omit precise coordinates for sensitive interventions unless user has `ADMIN` role; allow rounding to 2 decimal places for dispatchers via query parameter.
  - Audit logging for dashboard access to support compliance (store role, timestamp, filters applied).
