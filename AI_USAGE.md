# AI Usage

This document records the running log of AI tools, prompts, architecture designs, and line-by-line human reviews conducted throughout the engineering of **Zoner**.

---

## 1. Tools Used

| Tool | Role & Scope |
|---|---|
| **Google Antigravity IDE** | Development environment, automated tool execution, terminal orchestration, background test verification |
| **Claude 3.7 Sonnet** | Architectural design, pair-programming, scaffolding data models, implementing complex algorithms (recurrence expansion, timezone math, MCP transport, token hashing) |
| **Cursor / Claude Desktop** | MCP tool integration testing, verification of `list_calendars`, `create_event`, and `check_availability` |

---

## 2. Milestone Log: What AI Generated vs. Reviewed & Modified

| Milestone | AI-Generated Code & Artifacts | Reviewed, Verified & Refactored by Human | Outcome & Quality Gates |
|---|---|---|---|
| **Planning** | Initial engineering plan (`PLAN.md`), technology stack trade-offs, ADR drafts (`docs/adr/`) | Selected Java 21 + Spring Boot 3.5.x, Neon Serverless Postgres, and separate Render deployment model. Formulated strict DST wall-clock preservation requirements. | Architecture baseline established. |
| **M0: Skeleton & Probes** | Spring Boot 3.5 project scaffold, `CorrelationIdFilter`, `GlobalExceptionHandler`, Actuator `/healthz` & `/healthz/liveness` probes, Testcontainers setup | Added `.github/workflows/ci.yml`, tightened `.gitignore`, verified Swagger UI 302->200 redirect assertions in `ApplicationSmokeTest`, ran Testcontainers against PostgreSQL 16 container. | 16 tests passing, 0 failures. Probes and OpenAPI docs operational. |
| **M1: Auth & Users** | `User` & `RefreshToken` entities, `V2__auth_and_users.sql` Flyway migration, Spring Security config, `JwtService`, `AuthService`, `AuthController`, `UserController`, DTOs | Enforced SHA-256 token hashing for refresh token rotation, case-insensitive email indexing in PostgreSQL (`LOWER(email)`), IANA time zone validation via `ZoneId.of()`, and domain event publishing (`UserRegisteredEvent`). | 26 tests passing, 0 failures. Tested duplicate email 409, invalid credentials 401, token rotation. |
| **M2: Calendars & Shares** | `Calendar`, `CalendarShare`, `UserCalendarPref` entities, `V3__calendars_and_shares.sql`, `CalendarService`, `CalendarController`, `AccessPolicy` | Enforced strict `AccessPolicy` security matrix: returns 404 for stranger requests (preventing ID enumeration), explicit 403 for unauthorized edits, auto-provisions default "Personal" calendar on signup, supports per-user visibility toggles and custom color overrides. | 31 tests passing, 0 failures. Tested sharing lifecycle, permissions matrix. |
| **M3: Events & Availability** | `Event`, `Reminder` entities, `V4__events.sql`, `EventRepository`, `EventService`, `AvailabilityService`, `EventController`, DTOs | Implemented UTC instant vs local wall-clock computation, optimistic locking with `@Version` and `saveAndFlush`, interval overlap conflict checking (`startA < endB AND endA > startB`), debounced search, and anti-enumeration 404 enforcement. | 39 tests passing, 0 failures. Covered CRUD, optimistic lock 409, conflict warnings, availability checks. |
| **M4: Recurrence & Exceptions** | `EventException` entity, `V5__event_exceptions.sql`, `EventExceptionRepository`, `RecurrenceExpander`, `LibRecurExpander`, `RecurrenceIntegrationTest` | Implemented RFC 5545 RRULE expansion using `lib-recur`, DST-safe local wall-clock preservation across daylight saving changes, three standard recurrence edit modes (`THIS` single-occurrence override, `THIS_AND_FOLLOWING` series split, `ALL` master update), and single-occurrence cancellation exceptions. | 44 tests passing, 0 failures. Tested recurring standup lifecycle, DST transitions, and exception edits. |
| **M5: Reminders & Alerts** | `ReminderDispatch` & `Notification` entities, `V6__reminders_and_notifications.sql`, `ReminderDispatcher` worker, `NotificationService`, `NotificationController` | Ensured free-tier catch-up safety with rolling lookback window on cold start / wake-up, strict idempotency via PostgreSQL unique constraint `(reminder_id, occurrence_start)`, recurring occurrence expansion, and full in-app notification REST API. | 47 tests passing, 0 failures. Verified due calculation, rapid scheduler runs without duplicates, notification lifecycle. |
| **M6: Frontend Shell** | Vite + React 18 scaffold, Tailwind CSS, SVG emblem, Inter typography, API client with JWT refresh queue and correlation ID injection, `errors.js`, `AuthContext`, `ProtectedRoute`, `LoginPage`, `RegisterPage`, `AppShell`, `Sidebar` | Tested Vite proxy to backend, verified production bundle generation via `npm run build` (232 kB bundle, 0 errors), ensured Node v22 LTS runtime compatibility, and verified unread notification badge polling. | Dev server boots in ~106 ms, production build successful (`built in 6.48s`). |
| **M7: FullCalendar UI** | FullCalendar integration (`CalendarView.jsx`), Month/Week/Day views, interactive slot selection & click-to-create, `EventModal.jsx` (title, calendar, color, time pickers, all-day toggle, RRULE recurrence, reminders, conflict alerts), `EventDetailModal.jsx`, `RecurringChoiceModal.jsx`, `CreateCalendarModal.jsx`, `ShareCalendarModal.jsx`, `SearchModal.jsx` (`⌘K`), `ToastContext.jsx` | Customized FullCalendar CSS variables for clean brand styling, verified optimistic UI for drag-and-drop and resize with server rollback on error, verified debounced availability checking to warn about overlapping events, and built production bundle. | Production build successful (`built in 6.99s`). Full reviewer path operational in browser. |
| **M8: MCP Server** | Model Context Protocol server: Personal Access Token (PAT) entity & migration (`V7`), SHA-256 token hashing, `TokenService`, `TokenController`, `JwtAuthenticationFilter` multi-token support, `McpService` with all 9 calendar tools, `McpController` (Streamable HTTP + SSE), `SettingsModal.jsx` with PAT generation and client configuration snippets, `docs/MCP_AUTH.md` | Verified SHA-256 token hashing and one-time plaintext return, verified anti-enumeration 404 security enforcement across tools, verified view-only share rejection when scheduling via AI, verified automatic calendar resolution (by name, ID, or fallback to default), ran automated unit tests (`McpServiceTest`, `TokenServiceTest`). | 100% test pass rate (13 unit tests, 0 failures). MCP server compliant with protocol version `2024-11-05`. Tested with Cursor and Claude Desktop. |
| **M9: Cloud Deployment** | Multi-stage `backend/Dockerfile` with Java 21 Alpine & memory tuning (`-Xmx350m -XX:+UseSerialGC`), Render Blueprint `render.yaml` defining backend Web Service and frontend Static Site with SPA rewrites (`/* -> /index.html`), `DataSeeder.java` for demo accounts and sample events | Verified multi-stage Docker build structure, tested JVM memory arguments under 512 MB Free Tier limit, verified frontend production build with Vite (`built in 7.09s`), verified automated seed script idempotency on live Neon PostgreSQL database. | Live deployment operational: Frontend on `zoner-6uv2.onrender.com`, Backend on `zoner-backend.onrender.com`. |
| **M10: Polish & Documentation** | Comprehensive User & Reviewer Guide (`docs/HOW_TO_USE.md`), Engineering Decision Log (`docs/DECISIONS.md`), ADRs (`001` through `007`), complete `README.md` with system architecture & ERD, UI polish (10m short event rendering, placeholders) | Reviewed and verified all documentation against the live deployed services, verified clean build pipelines, verified incognito reviewer path. | Submission checklist 100% complete. |

---

## 3. Key Human-in-the-Loop Interventions & Design Decisions

Throughout development, AI output was scrutinized line by line to prevent subtle production bugs:

1. **DST Wall-Clock Preservation (M4):**
   * *AI Tendency:* LLMs frequently convert recurring event times to UTC instants immediately and store them as UTC timestamps.
   * *Human Intervention:* Enforced storing wall-clock time (`10:00:00`) + IANA timezone (`America/New_York`). If a user schedules a daily 10:00 AM standup, it must remain at 10:00 AM local time across DST transitions, even though its UTC instant shifts by $\pm 1$ hour.
2. **Anti-Enumeration 404 Security (M2 & M8):**
   * *AI Tendency:* LLMs typically return `403 Forbidden` for any unauthorized access.
   * *Human Intervention:* Refactored `AccessPolicy` to return `404 Not Found` when a user attempts to read or mutate a resource they do not own or have shared with them. Returning `403` leaks that the ID exists, allowing enumeration attacks. Explicit `403` is reserved only for authenticated collaborators attempting mutations on `VIEW`-only calendars.
3. **Dispatcher Idempotency on Wake-Up (M5):**
   * *AI Tendency:* Relying solely on in-memory timers or Spring `@Scheduled` flags.
   * *Human Intervention:* Added a PostgreSQL composite unique constraint on `reminder_dispatches(reminder_id, occurrence_start)` combined with a rolling lookback window. This guarantees zero duplicate notifications even if multiple container instances boot simultaneously or wake up from sleep.
4. **JVM Memory Tuning for Free-Tier Hosting (M9):**
   * *AI Tendency:* Standard Spring Boot Dockerfiles without memory constraints, leading to OOM kills on Render's 512 MB free tier.
   * *Human Intervention:* Explicitly configured JVM flags in `backend/Dockerfile`: `-Xms128m -Xmx350m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m` to guarantee memory stability.
5. **CORS Regex Matching for Render Static Subdomains (M9):**
   * *AI Tendency:* Static comma-separated `localhost:5173`.
   * *Human Intervention:* Added regex matching (`https://.*\.onrender\.com`) to both `application.yml` and `SecurityConfig.java` to support dynamic Render static site preview URLs without CORS failures.
