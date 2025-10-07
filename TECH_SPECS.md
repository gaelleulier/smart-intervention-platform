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
