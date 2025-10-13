# AI Development Log

Context : Smart Intervention Platform (SIP) is a web application for managing and supervising field interventions.
It allows administrators, managers, and technicians to plan, monitor, and analyze operations via a centralized dashboard.
The goal is to improve responsiveness, traceability, and operational efficiency through a modern, secure, and data-driven architecture.

🗣️ Always respond in French.

🧾 Write all code, filenames, and technical files (e.g., README, YAML, JSON) in English.

📂 Always refer to this file (AI_DEV_LOG.md) as the single source of truth for project context, roadmap, and updates.

🔄 Update this file regularly whenever new features, modules, or architecture changes are introduced.

🧾 Explain exactly what you did, what changed, and why, to resolve the  issue, so that I can understand in detail.

💬 After reading this file, always confirm by writing the sentence:

"✅ Context loaded from AI_DEV_LOG.md."

🧠 Use the “Entry Template” below to append updates:

## Entry Template
### YYYY-MM-DD-T:HH:MM:SS - <Short title>
- Summary:
  - <bullet(s) describing completed work>
- Savepoints:
  - <list executed or planned savepoints; use `None` if not applicable>
- Required updates:
  - [ ] Update GitLab CI (.gitlab-ci.yml) IF needed
  - <additional docs/CI updates as checkbox items>
- Notes:
  - <optional remarks>

---

### 2025-10-08-T:20:05:00 - Restore markers after refresh
- Summary:
  - Triggered Leaflet marker updates immediately after reloading map data, ensuring points stay visible while the map stays mounted.
  - Kept the initialization guard so freshly mounted maps still pick up the latest markers once ready.
- Savepoints:
  - None
- Required updates:
  - [ ] Update GitLab CI (.gitlab-ci.yml) IF needed
- Notes:
  - Test with the dashboard refresh button; markers should persist without a full page reload.

### 2025-10-08-T:20:15:00 - Align dashboard refresh button with primary blue
- Summary:
  - Restyled the dashboard refresh CTA to reuse the primary blue palette (`#2563eb`) already present on interventions and users screens.
  - Added hover/focus feedback matching the existing UI buttons while keeping the loading disabled state.
- Savepoints:
  - None
- Required updates:
  - [ ] Update GitLab CI (.gitlab-ci.yml) IF needed
- Notes:
  - Visually confirm the new hover tone (`#1d4ed8`) in the dashboard header.

### 2025-10-08-T:20:45:00 - Introduce analytics charts on dashboard
- Summary:
  - Integrated `ng2-charts`/Chart.js to surface four visuals: interventions per day (line), status mix (stacked bar), technician workload (horizontal bar), and real-time distribution (donut).
  - Built reactive datasets with Angular signals reusing `statusTrends`, `technicianLoad`, and the summary, including graceful fallbacks when data is missing.
  - Refreshed the SCSS layout to host the chart grid alongside the existing Leaflet map.
- Savepoints:
  - None
- Required updates:
  - [ ] Update GitLab CI (.gitlab-ci.yml) IF needed
- Notes:
  - Install new frontend dependencies: `npm install` (ng2-charts pinned to `^5.0.4`).

### 2025-10-08-T:21:05:00 - Seed Toulouse-centric demo dataset
- Summary:
  - Added migration `V12__seed_toulouse_demo_data.sql` inserting dispatchers, technicians, and twelve geo-tagged interventions around Toulouse with varied statuses.
  - Pre-populated analytics tables (`intervention_daily_metrics`, `intervention_technician_load`, `intervention_geo_view`) so the charts show data immediately.
- Savepoints:
  - None
- Required updates:
  - [ ] Update GitLab CI (.gitlab-ci.yml) IF needed
- Notes:
  - Apply via `make backend-run` or `./mvnw flyway:migrate` to load the new migration.

### 2025-10-08-T:21:20:00 - Register French locale for dashboard charts
- Summary:
  - Registered the `fr-FR` locale with `registerLocaleData` in `main.ts` and set `LOCALE_ID` application-wide to unlock date pipes in charts.
- Savepoints:
  - None
- Required updates:
  - [ ] Update GitLab CI (.gitlab-ci.yml) IF needed
- Notes:
  - Restart the frontend (`npm start`) so locale registration takes effect.

### 2025-10-08-T:21:30:00 - Stabilize root app unit test after layout refactor
- Summary:
  - Updated `app.spec.ts` to assert the presence of the `router-outlet` instead of the legacy title removed from the root component.
- Savepoints:
  - None
- Required updates:
  - [ ] Update GitLab CI (.gitlab-ci.yml) IF needed
- Notes:
  - `npm test` should pass again.

### 2025-10-08-T:21:55:00 - Enrich dashboard UX & data coverage
- Summary:
  - Added a shared loader for chart cards, a manual refresh button (admins), and confirmation feedback after the `/refresh` call.
  - Extended the Toulouse seed with four more interventions across the 14-day window to improve trend density.
  - Introduced `dashboard-page.component.spec.ts` to cover data aggregation and the refresh flow.
- Savepoints:
  - None
- Required updates:
  - [ ] Update GitLab CI (.gitlab-ci.yml) IF needed
- Notes:
  - Rebuild the frontend and rerun `make backend-run` to replay migration V12.

### 2025-10-09-T:16:40:00 - Allow safe technician deletion
- Summary:
  - Added automatic detachment of interventions when a user is removed while blocking deletion of the last admin.
  - Normalized the HTTP 409 response via `UserExceptionsHandler` so the UI gets an explicit error message.
  - Strengthened logging in the JWT filter to ease troubleshooting.
- Savepoints:
  - None
- Required updates:
  - [ ] Update GitLab CI (.gitlab-ci.yml) IF needed
- Notes:
  - Restart `make backend-run` after rebuilding to pick up the backend change.

### 2025-10-09-T:17:10:00 - Enable intervention deletion & map picker
- Summary:
  - Enabled ADMIN/DISPATCHER roles to delete interventions (`DELETE /api/interventions/{id}`) with frontline support in Angular.
  - Added a Leaflet map picker in create/edit forms so latitude/longitude are set by clicking on the map.
  - Surfaced a “Delete” action in the interventions table with visual feedback and auto-refresh.
- Savepoints:
  - None
- Required updates:
  - [ ] Update GitLab CI (.gitlab-ci.yml) IF needed
- Notes:
  - After updating, restart the backend and `npm start` to activate the map picker.

### 2025-10-09-T:19:45:00 - Harden auth cookies & technician scope
- Summary:
  - Issued JWTs in HttpOnly cookies with new session/logout endpoints and aligned Angular to drop localStorage usage.
  - Updated the frontend auth guard/interceptor to await session hydration and send credentials with every API request.
  - Restricted intervention listings and detail reads so technicians only access their assigned work-orders.
- Savepoints:
  - None
- Required updates:
  - [ ] Update GitLab CI (.gitlab-ci.yml) IF needed
- Notes:
  - Frontend unit tests need `CHROME_BIN` set when running `npm test`.

### 2025-10-11-T:10:30:00 - Introduce lightweight AI assistants
- Summary:
  - Exposed `/api/dashboard/ai/insights` and `/api/dashboard/ai/forecast` endpoints computing heuristics from analytics tables (trend deltas, validation ratio, SLA check, exponential smoothing forecast sur 7 jours).
  - Added `/api/interventions/recommendation` with un modèle d’assignation local mixant distance Haversine, charge ouverte (analytics) et historique d’interventions similaires.
  - Intégré côté Angular : carte “AI Insights”, widget de prévision et formulaire Smart Assignment sur le dashboard.
  - Habillage UI “IA” (header dédié, icônes, badges, gradients, animations) avec états explicables pour la recommandation (idle/thinking/result + anneau conic-gradient, CTA Appliquer/Alternatives).
- Savepoints:
  - None
- Required updates:
  - [ ] Update GitLab CI (.gitlab-ci.yml) IF needed
- Notes:
  - Prévision calculée côté backend (α=0.5). Les scores Smart Assignment restent heuristiques pour hébergement low-spec.

### 2025-10-13-T:11:34:41 - Automate demo intervention simulator
- Summary:
  - Added a scheduled `InterventionDemoSimulator` that every ten minutes generates one to three synthetic interventions with randomized timestamps, geo-coordinates, and status mix within the last thirty minutes.
  - Ensured idempotent creation of the `idx_interventions_created_at` index, exposed retention and batch purge thresholds via `DEMO_MAX_ROWS`/`DEMO_BATCH_DELETE`, and looped batched deletions until the table drops back under the configured cap.
  - Introduced unit coverage (`InterventionDemoSimulatorTest`) to validate generation boundaries, index handling, and purge loop behaviour.
- Savepoints:
  - None
- Required updates:
  - [ ] Update GitLab CI (.gitlab-ci.yml) IF needed
- Notes:
  - Executed `./mvnw test` to validate the backend module.

### 2025-10-13-T:11:55:17 - Add observability & auto tech assignment
- Summary:
  - Logged each scheduler run with timestamp, configured caps, synthetic intervention payloads, and detailed purge iteration feedback in `InterventionDemoSimulator`.
  - Assigned every synthetic intervention to a random technician (role TECH) and skipped runs when none are available, mirroring real operational data.
  - Relaxed the bbox assertions in `InterventionDemoSimulatorTest` by reflecting simulator bounds so tests stay aligned with future tuning.
- Savepoints:
  - None
- Required updates:
  - [ ] Update GitLab CI (.gitlab-ci.yml) IF needed
- Notes:
  - Re-ran `./mvnw test` to confirm the scheduled simulator behaviour.
