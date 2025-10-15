# Smart Intervention Platform

Smart Intervention Platform (SIP) centralizes the planning, supervision, and analysis of field interventions. The project bundles a Spring Boot backend, an Angular frontend, and a PostgreSQL database to deliver a secure, data-driven experience.

---

## Architecture

```
                ┌──────────────────────────┐
                │        Browser UI        │
                └─────────────┬────────────┘
                              │ HTTPS
        ┌─────────────────────▼─────────────────────────────┐
        │ Angular app                                       │
        │ • Dev: Angular dev server (http://localhost:4200) │
        │ • Prod: Nginx serves the bundle and proxies /api  │
        └─────────────────────┬─────────────────────────────┘
                              │ REST (JSON)
                  ┌───────────▼───────────┐
                  │ Spring Boot backend   │
                  │ • HttpOnly JWT auth   │
                  │ • Dashboard + AI      │
                  │ • Demo simulator      │
                  └───────────┬───────────┘
                              │ JDBC / Flyway
                  ┌───────────▼───────────┐
                  │ PostgreSQL 16         │
                  │ • Operational data    │
                  │ • Analytics tables    │
                  └───────────────────────┘
```

* **Frontend**: Angular 18 (Node 22). In production Nginx ships the static bundle and reverse proxies `/api` to the backend container.
* **Backend**: Spring Boot 3.5 (Java 21). Exposes REST APIs, handles authentication with HttpOnly cookies, delivers AI insights, and runs scheduled demo simulators.
* **Database**: PostgreSQL 16 with Flyway migrations, Toulouse demo seed, and analytics tables consumed by the dashboard.

---

## Key Capabilities

- Real-time dashboard with Leaflet map and Chart.js visuals (daily trend, status mix, technician workload, live distribution).
- AI assistants for insights, 7-day forecast, and technician recommendation with transparent rationales.
- Intervention module: create/update with map picker, delete, filter, assign.
- User management: ADMIN/DISPATCHER/TECH roles, safe deletion rules, password change flow.
- Hardened authentication: HttpOnly cookies, session hydration guard, role-based access restrictions.
- Built-in Toulouse dataset and scheduled simulator creating synthetic interventions—no external demo datasets required.

---

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.5.x, Spring Data JPA, Flyway, Actuator.
- **Frontend**: Angular 18, TypeScript, SCSS, Leaflet, ng2-charts/Chart.js.
- **Database**: PostgreSQL 16, HikariCP.
- **Ops**: Docker / Docker Compose v2, Nginx (prod), Makefile, GitLab CI (build & deploy).

---

## Repository Layout

```
smart-intervention-platform/
├─ backend/                     # Spring Boot application
│  ├─ src/main/java/...         # APIs, domain, security, simulators
│  ├─ src/main/resources/       # application*.yml, Flyway migrations
│  └─ Dockerfile                # Backend image (JAR)
├─ frontend/                    # Angular application + SSR bundle
│  └─ Dockerfile                # Frontend image (Nginx)
├─ docker-compose.dev.yml       # Development stack (Postgres)
├─ docker-compose.prod.yml      # Production stack (db + backend + frontend)
├─ Makefile                     # Handy commands (dev/prod/tests)
├─ scripts/                     # Infra scripts (e.g. Flink deps)
├─ infra/                       # Deployment / CI assets
```

---

## Environments

### Development

- PostgreSQL runs in Docker (`docker-compose.dev.yml`).
- Backend: `dev` profile, `./mvnw spring-boot:run`, exposed on `http://localhost:8080`.
- Frontend: `npm run start`, Angular proxy forwards `/api`.


---

## Prerequisites

- Docker + Docker Compose v2 (`docker compose` command).
- Java 21 (Temurin recommended).
- Node.js 22 + npm (install via `nvm` is convenient).
- Make (for the provided shortcuts) and `jq` for quick JSON checks.

---

## Configuration

Clone the repository and copy the environment variables template:

```bash
cp .env.example .env
```

Essential variables:

```env
PROJECT_NAME=smart-intervention-platform
POSTGRES_USER=sip_user
POSTGRES_PASSWORD=sip_password
POSTGRES_DB=sip_db
SPRING_PROFILES_ACTIVE=dev
BACKEND_PORT=8080           # Development only
FRONTEND_PORT=4200          # Development only
JWT_SECRET=change-me        # Required for JWT cookies
```

In production the frontend is the only container exposing a port (80 by default); the backend stays internal to the Docker network.

---

## Run – Development

> Use three terminals at the repository root.

1. **Database**
   ```bash
   make env-up
   docker compose -f docker-compose.dev.yml --env-file .env ps
   ```
2. **Backend**
   ```bash
   make backend-run
   curl -s http://localhost:8080/api/health | jq
   ```
3. **Frontend**
   ```bash
   make frontend-run
   # Opens http://localhost:4200
   ```

Log in with the seeded administrator (`admin@sip.local` / `Admin123!`) or use the “Try as Dispatcher/Technician” shortcuts on the login screen.

To stop everything: `Ctrl+C` in backend & frontend terminals, then `make env-down` (append `-v` to reset the database).

---

## Demo Data

- `V12__seed_toulouse_demo_data.sql` loads interventions around Toulouse.
- `InterventionDemoSimulator` (runs every 10 minutes) inserts synthetic interventions and prunes the table when exceeding configurable thresholds (`DEMO_MAX_ROWS`, `DEMO_BATCH_DELETE`).
- To disable the simulator in production, set `DEMO_SIMULATOR_ENABLED=false` (environment variable or `application-prod.yml` override).

---

## Run – Production (local rehearsal)

1. Review `.env` (optionally override `FRONTEND_PORT`, set a strong `JWT_SECRET`).
2. Build and start:
   ```bash
   make prod-up
   ```
3. Check status:
   ```bash
   make prod-ps
   curl -s http://localhost:${FRONTEND_PORT}/api/health | jq    # via the Nginx proxy
   ```
4. Tail logs:
   ```bash
   make prod-logs
   ```
5. Stop the stack:
   ```bash
   make prod-down
   ```

Resource limits (`deploy.resources`) keep the stack within the VPS budget so additional apps can run alongside SIP.

---

## Make Targets

```makefile
env-up / env-down    # Start/stop Postgres (dev)
backend-run          # Spring Boot dev profile
frontend-run         # Angular dev server
prod-up / prod-down  # Full Docker stack
prod-logs / prod-ps  # Inspect production stack
db-cli               # psql inside the Postgres container
```

---

## Database & Migrations

- Migrations live in `backend/src/main/resources/db/migration`.
- Flyway executes on backend startup (dev & prod).
- Analytics tables are hydrated by the backend—no external pipeline required.
- Inspect with:
  ```bash
  make db-cli
  \dt
  SELECT * FROM flyway_schema_history ORDER BY installed_rank;
  ```

---

## Security

- Authentication uses HttpOnly JWT cookies issued by `/api/auth/login` and cleared via `/api/auth/logout`.
- The Angular guard waits for session hydration before routing into protected areas.
- Role-based permissions: `ADMIN`, `DISPATCHER`, `TECH` (e.g. technicians only access their own interventions).
- Store sensitive variables (`JWT_SECRET`, database credentials) in a secret manager or protected CI variables.

---

## Testing & Quality

- Backend: `./mvnw test`.
- Frontend: unit tests and linting are being introduced; refer to `AI_DEV_LOG.md` for the current status.
- Before shipping, run the relevant test suites and exercise the Dockerized stack with `make prod-up`.

---

## License

TBD.
