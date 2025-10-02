# Smart Intervention Platform

A modern web platform to orchestrate field interventions (work orders, scheduling, technicians, reporting). This repository is a **monorepo** containing the backend (Spring Boot), frontend (Angular), and ops assets (Docker Compose, Makefile).

> Development mode: **Postgres runs in Docker**; **backend** and **frontend** run locally for fast feedback.

---

## Architecture

```
+-----------------+        REST        +-----------------------+         +----------------+
|  Angular (SPA)  |  <-------------->  |  Spring Boot (API)    |  <----> |  Postgres 16   |
|  http://:4200   |                    |  http://localhost:8080|         |  Docker (dev)  |
+-----------------+                    +-----------------------+         +----------------+
```

* **Frontend**: Angular (TypeScript), dev server with proxy to `/api`.
* **Backend**: Spring Boot (Java 21), JPA/Hibernate, Flyway, Actuator.
* **Database**: PostgreSQL 16 (Dockerized in development).

---

## Tech Stack

* **Java 21**, **Spring Boot 3.5.x**, **Spring Data JPA**, **Flyway**, **Actuator**
* **Angular 18** (Node 22), **SCSS**
* **PostgreSQL 16**, **Docker Compose v2**
* **Makefile** for developer ergonomics

---

## Repository Structure

```
smart-intervention-platform/
├─ backend/                     # Spring Boot application
│  ├─ src/main/java/...         # API, domain, config
│  ├─ src/main/resources/       # application*.yml, Flyway migrations
│  └─ pom.xml                   # Maven build (wrapper: mvnw)
├─ frontend/                    # Angular application (created via Angular CLI)
├─ docker-compose.dev.yml       # Dev: Postgres only
├─ docker-compose.prod.yml      # Prod: skeleton (all services containerized)
├─ Makefile                     # Common tasks (DB up/down, run apps)
└─ .env.example                 # Sample environment variables
```

> If the `frontend/` folder is not present yet, generate it with Angular CLI (see **Prerequisites** & **Run – Development**).

---

## Environments

* **Development**

  * Postgres runs in Docker (`docker-compose.dev.yml`).
  * Backend runs locally with profile `dev` (datasource points to the Docker DB).
  * Frontend runs with Angular dev server and a proxy to the backend.
* **Production**

  * All services containerized. `docker-compose.prod.yml` is provided as a skeleton; Dockerfiles are introduced in a later milestone.

---

## Prerequisites

* **Docker** + **Docker Compose v2** (`docker compose` command)
* **Java 21** (Temurin recommended)
* **Node 22** + **npm** (via `nvm` recommended)

Optional but recommended: VS Code + Java/Angular/Docker extensions.

---

## Configuration

Copy the example file and adjust values if needed:

```bash
cp .env.example .env
```

Minimal `.env` (excerpt):

```env
PROJECT_NAME=smart-intervention-platform
POSTGRES_USER=sip_user
POSTGRES_PASSWORD=sip_password
POSTGRES_DB=sip_db
POSTGRES_PORT=5432
POSTGRES_HOST=localhost
SPRING_PROFILES_ACTIVE=dev
BACKEND_PORT=8080
FRONTEND_PORT=4200
```

---

## Run – Development (3 commands)

> Open **three terminals** at the repository root.

### 1) Database (Docker)

```bash
make env-up
# Equivalent: docker compose -f docker-compose.dev.yml --env-file .env up -d
```

Check status:

```bash
docker compose -f docker-compose.dev.yml --env-file .env ps
# db service must be "healthy"
```

### 2) Backend (Spring Boot)

```bash
make backend-run
# Equivalent: cd backend && SPRING_PROFILES_ACTIVE=dev ./mvnw -q -DskipTests spring-boot:run
```

Health check:

```bash
curl -s http://localhost:8080/api/health | jq
# { "status": "UP", "db": 1 }
```

### 3) Frontend (Angular)

Ensure Node 22 is active in this terminal (e.g., `nvm use 22`). Then:

```bash
make frontend-run
# Equivalent: cd frontend && npm install && npm run start
```

Open the UI: `http://localhost:4200` (you will be redirected to the login screen).

---

## Stop – Development

Stop app servers with `Ctrl + C` in their terminals.

Stop the database:

```bash
make env-down
# Equivalent: docker compose -f docker-compose.dev.yml --env-file .env down -v
```

> `-v` removes volumes (database is wiped). Omit `-v` if you want to keep data.

---

## Make Targets

```makefile
env-up         # Start Postgres (dev)
env-down       # Stop Postgres and REMOVE volumes (-v)
backend-run    # Run backend with dev profile
frontend-run   # Run Angular dev server (proxy /api)
db-cli         # psql inside the Postgres container
env-ps         # (optional) Show compose services status
db-logs        # (optional) Tail Postgres container logs
```

---

## Database & Migrations

* Migrations live in `backend/src/main/resources/db/migration` and are applied by **Flyway** on backend startup.
* Current scripts provision the `users` table (unique email, password hash, roles) and seed a bootstrap administrator.

Inspect from the container:

```bash
make db-cli
# psql> \dt
# psql> SELECT * FROM flyway_schema_history ORDER BY installed_rank;
# psql> SELECT * FROM demo;
```

---

## Users Module (MVP)

* **Backend API** (`/api/users`): supports pagination (`page`, `size`), search (`query` matches email/full name), and role filtering (`role=ADMIN|DISPATCHER|TECH`) alongside full CRUD endpoints. Creation and updates require a password (min. 8 characters including letters and digits).
* **Frontend UI**: sign in at `http://localhost:4200/login`, then manage accounts at `http://localhost:4200/users` (list, filter, create, edit, delete). A “Change your password” panel updates the currently logged-in user and forces re-authentication.
* Validation and errors: API returns RFC 7807 `ProblemDetail` payloads; the UI surfaces any backend error inline for faster troubleshooting.

---

## Security

* Authentication uses stateless JWT tokens (`Authorization: Bearer <token>`). Obtain a token via `POST /api/auth/login` or the `/login` form.
* The bootstrap administrator (`admin@sip.local` / `Admin123!`) is provisioned by Flyway; update this password immediately outside local development.
* The signing secret comes from `JWT_SECRET` (see `.env.example`). Provide it through a secret store (Vault, GitLab protected variables, Kubernetes secrets, etc.) and rotate it whenever you want to invalidate existing tokens.
* In GitLab CI, declare `JWT_SECRET` as a protected/masked variable so pipelines can sign tokens without exposing the key.
* `POST /api/auth/change-password` lets an authenticated user change their own password; the client logs out afterwards to require a fresh login.
* Access rules:
  * Every `/api/users/**` endpoint requires a valid JWT.
  * `POST/PUT/DELETE /api/users/**` remain restricted to users with the `ADMIN` role.

## Health Endpoints

* `GET /api/health` – application + database ping (dev convenience endpoint)

---

## CI/CD (GitLab)

A GitLab pipeline is planned for future milestones (build, test, containerize, deploy). Compose production files are provided as a starting point.

---

## Contributing

* Use feature branches and merge requests.
* Keep commits small and meaningful; include tests where applicable.
* Follow code style conventions of Spring/Angular ecosystems.

---

## License

TBD.
