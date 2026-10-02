# Zoner: Engineering Plan

Senior full-stack assignment. Java backend, any frontend, public deployment, MCP server.
This plan is written to be committed to the repo (as `docs/PLAN.md`) and updated as we go.

---

## 0. How a senior submission differs

Reviewers will check that the features exist, but at senior level they mostly check **judgement**:

1. **Hard problems solved properly**: recurrence, time zones, and authorization are where junior submissions fall apart.
2. **One source of truth**: REST and MCP both call the same service layer, so permissions are enforced once.
3. **Defensible decisions**: every non-obvious choice gets a short ADR (Architecture Decision Record) in `docs/adr/`, because you will be asked to explain or modify things live.
4. **Reviewer experience**: URL → Login → Calendar → Create → Search → Share → MCP must work with zero help.
5. **Honesty about scope**: a polished core beats a half-working bonus list. A "Known limitations" section in the README is a strength.

---

## 1. Key technical decisions

| Area | Decision | Why |
|---|---|---|
| Backend | Java 21 (17+ required), Spring Boot 3.x, Spring Data JPA, Spring Security | Matches the brief and your experience |
| DB | PostgreSQL + **Flyway** migrations | Real relational model, reproducible schema, `timestamptz` support |
| Auth (web) | Email/password (BCrypt), short-lived JWT access token + refresh token (httpOnly cookie) | Stateless API, safe logout, no tokens in localStorage |
| Auth (MCP) | **Personal Access Tokens** created in the app's settings, hashed in DB, sent as `Authorization: Bearer` (OAuth 2.1 as a documented stretch) | Simple, secure, revocable, scoped, and easy for a reviewer to configure |
| API docs | springdoc-openapi (Swagger UI at `/swagger-ui`) | Required deliverable |
| Tests | JUnit 5, Mockito, **Testcontainers (Postgres)**, MockMvc | Real DB behaviour for conflict, search and sharing tests |
| Frontend | React (JavaScript) + Vite, Tailwind, TanStack Query | Fast to build, good cache/optimistic-update story. Plain JS by choice; we use JSDoc on API helpers and PropTypes-free, small components to keep it safe |
| Calendar UI | **FullCalendar** (MIT core: dayGrid, timeGrid, interaction) with custom styling | Drag, resize and slot-click come free; time goes into branding and UX |
| Recurrence | Store RFC 5545 `RRULE` (supported subset), expand at query time, exceptions in a separate table | Required: no hundreds of rows |
| MCP | MCP server **inside the same Spring Boot app** (Spring AI MCP server starter, Streamable HTTP/SSE, verify the current version when we start) | Same DB, same services, one deploy, one URL |
| Deployment Architecture | **Separated deployments**: Frontend on Vercel/Netlify; Backend on Render/Railway. Single environment via `.env` | Independent builds and scaling, clean separation of concerns |
| Database | **Single deployed cloud PostgreSQL** (Neon/Supabase/Render Postgres) across the board. No local Docker DB | Single source of truth, eliminates local container drift |
| Hosting (zero-cost) | Frontend on Vercel/Netlify + Backend on Render + **Neon free Postgres** | ₹0 cost, fast static CDN for UI, robust API runtime |

> **Architectural Decision:** Split frontend/backend deployments. The backend exposes standard REST endpoints and MCP with CORS enabled (`CORS_ALLOWED_ORIGINS`). The frontend connects via HTTP API with credentials.

> **Deployment risk:** free tiers sleep (cold starts of 30-60 s). Add a `/healthz` endpoint, a loading state in the UI, and warm the instance before submitting. Document this in the README.

---

## 2. Architecture

```
 Web Client (React SPA) ──┐
                          ├──► Spring Boot app ──► PostgreSQL
 AI Assistant ─► MCP ─────┘      │
 (Claude, Cursor…)               ├─ REST controllers  (/api/**)
                                 ├─ MCP tool adapters (/mcp)
                                 ├─ Application services  ◄── single source of business rules
                                 │    EventService, RecurrenceService, CalendarService,
                                 │    SharingService, SearchService, AvailabilityService,
                                 │    ReminderService
                                 ├─ AccessPolicy (central authorization)
                                 └─ Scheduler → NotificationChannel (InApp | Email stub | SMS stub)
```

**Package layout** (feature-oriented with layers inside):

```
com.<yourname>.calendar
  auth/        users, jwt, refresh, personal access tokens, security config
  calendar/    calendars, shares, access policy
  event/       events, recurrence, exceptions, search, availability
  reminder/    reminder config, dispatcher, notification channels
  mcp/         tool classes (thin adapters over services), MCP auth filter
  common/      error model, validation, logging, time utils
```

Rules:
- Controllers and MCP tools contain **no business logic**; they map input, call a service, map output.
- DTOs at the API boundary; entities never leak.
- Authorization lives in one place (`AccessPolicy`) and is called from services, not controllers.

---

## 3. Data model

```
users(id, email unique, password_hash, display_name, time_zone, created_at)

calendars(id, owner_id→users, name, description, color, is_default, created_at)

calendar_shares(id, calendar_id→calendars, user_id→users,
                permission ENUM(VIEW, EDIT), created_at,
                UNIQUE(calendar_id, user_id))

user_calendar_prefs(user_id, calendar_id, enabled, color_override)   -- per-user enable/disable of own AND shared calendars

events(id, calendar_id, title, description, location, color,
       all_day boolean,
       start_at timestamptz, end_at timestamptz,         -- the instant (non-recurring and the first occurrence)
       start_local timestamp, end_local timestamp,       -- wall-clock time, used for recurrence expansion and all-day
       time_zone text,                                   -- IANA id, e.g. Asia/Kolkata
       recurrence_rule text NULL,                        -- RRULE, e.g. FREQ=WEEKLY;BYDAY=MO
       recurrence_until timestamptz NULL,                -- denormalized for fast range filtering
       version bigint,                                   -- optimistic locking
       created_by, created_at, updated_at)

event_exceptions(id, event_id, original_start timestamptz,
                 type ENUM(CANCELLED, MODIFIED),
                 override_title, override_start_at, override_end_at, override_location, ..., 
                 UNIQUE(event_id, original_start))

reminders(id, event_id, minutes_before int, channel ENUM(IN_APP, EMAIL, SMS))

reminder_dispatches(id, reminder_id, occurrence_start, fire_at, status, 
                    UNIQUE(reminder_id, occurrence_start))   -- idempotency

notifications(id, user_id, event_id, occurrence_start, message, read_at, created_at)

api_tokens(id, user_id, name, token_hash, scopes, last_used_at, expires_at, revoked_at)

-- bonus: audit_log(id, user_id, entity, entity_id, action, diff_json, at)
```

**Indexes that matter:** `events(calendar_id, start_at, end_at)`, `calendar_shares(user_id)`, GIN/`pg_trgm` on title, description and location for search.

---

## 4. The three hard problems (design before code)

### 4.1 Time zones (show you understand instant vs local vs zone)

- **Instant** = a point on the universal timeline (`timestamptz`, stored as UTC). Right for one-off events.
- **Local date-time** = wall-clock time with no zone ("Monday 10:00"). Right for **recurring** events and **all-day** events.
- **Time zone** = an IANA id that turns local time into an instant on a given date.

Decisions:
- One-off events store `start_at`/`end_at` as instants plus the event's `time_zone` for display and editing.
- Recurring events expand from `start_local` + `time_zone`, so "standup every Monday at 10:00" stays at 10:00 across DST changes.
- All-day events are dates, not instants.
- The API speaks ISO-8601 with offsets. The user's profile time zone is the default for display. MCP tools accept a `timezone` argument and fall back to the user's profile zone.
- Tests: DST boundary (e.g. `America/New_York` in March and November), cross-zone creation, all-day across zones.

### 4.2 Recurrence

- Supported: DAILY, WEEKLY (with BYDAY), MONTHLY (by day-of-month, and optionally nth weekday), YEARLY, with `INTERVAL`, `UNTIL`, `COUNT`.
- Use a vetted library for expansion (ical4j or `lib-recur`) behind our own `RecurrenceExpander` interface. We do not hand-roll RRULE parsing.
- Range query flow: fetch non-recurring events overlapping `[from, to)`, plus recurring events whose series could intersect the range, then expand in memory, apply exceptions, and return **occurrences** (`eventId`, `occurrenceStart`, ...).
- Editing and deleting:
  - **This occurrence** → row in `event_exceptions` (CANCELLED or MODIFIED)
  - **This and following** → set `UNTIL` on the original series and create a new series from that date
  - **All occurrences** → edit the master event
- Cap expansion (max range and max occurrences per query) to protect the server.

### 4.3 Authorization and sharing

`AccessPolicy` is the single gate:

| Action | OWNER | EDIT | VIEW | No share |
|---|---|---|---|---|
| Read events | ✅ | ✅ | ✅ | ❌ 404 |
| Create/update/delete events | ✅ | ✅ | ❌ 403 | ❌ 404 |
| Rename/delete calendar | ✅ | ❌ | ❌ | ❌ |
| Manage shares | ✅ | ❌ | ❌ | ❌ |

- Return **404** (not 403) for resources the user has no relationship to, so we do not leak existence.
- Every service method takes the authenticated `UserPrincipal`. There is no "get by id" without a policy check.
- The same checks apply to MCP tools, because they call the same services.
- Tests cover the full matrix, including a user trying to reach another user's event by guessing IDs.

---

## 5. MCP design

**Tools (all 9 required):** `list_events`, `get_event`, `create_event`, `update_event`, `delete_event`, `search_events`, `list_calendars`, `create_calendar`, `check_availability`.

AI-friendly design:
- Clear tool descriptions with example inputs, ISO-8601 inputs with explicit `timezone`, and human-readable outputs (title, local time, calendar name, id).
- `create_event` accepts a calendar **name or id**, defaulting to the user's default calendar.
- Helpful errors, e.g. "Calendar 'Wrk' not found. Available: Work, Personal." and "You have view-only access to 'Family'."
- `check_availability` returns conflicting events, not just a boolean.
- Destructive tools (`delete_event`) describe themselves as destructive so clients can prompt for confirmation.

**Auth flow (documented in `docs/MCP_AUTH.md`):**
1. User creates a Personal Access Token in Settings → Integrations (shown once, stored as a SHA-256 hash).
2. MCP client sends `Authorization: Bearer <token>`.
3. An MCP auth filter hashes the token, looks it up, checks expiry and revocation, and resolves the **user**. That user becomes the `SecurityContext` principal.
4. Tools call the same services with that principal, so ownership and sharing rules apply exactly as in the web app.
5. No tool ever accepts a `userId` argument. Identity comes only from the token.
6. Tokens are revocable, optionally scoped (`calendar:read`, `calendar:write`), rate-limited, and logged (`last_used_at`).
7. Stretch: OAuth 2.1 with PKCE as MCP's recommended remote auth, described in the doc as the production path.

**Client config** for README: Claude Desktop (via `mcp-remote`), Cursor, and Claude Code (`claude mcp add --transport http ...`), plus a `curl` smoke test.

---

## 6. Reminders architecture

- A reminder is `minutes_before` + `channel`.
- A scheduled job (every minute) computes due reminders in a rolling window, expanding recurrences, and inserts `reminder_dispatches` rows. The unique constraint `(reminder_id, occurrence_start)` makes it **idempotent**, including across multiple instances.
- Dispatch goes through `interface NotificationChannel { void send(Notification n); }`:
  - `InAppChannel` (implemented): writes to `notifications`, shown in the bell menu via polling (SSE as a stretch)
  - `EmailChannel` (stub or optional SMTP)
  - `SmsChannel` (stub)
- The README includes a short "How to add email/SMS" section, as the brief explicitly asks.

---

## 7. Frontend and product plan

**Branding (decide first, takes 1 hour):**
- Product name, tagline, simple SVG logo, favicon, color tokens (primary, neutrals, 8 event colors), typography (e.g. Inter plus a display font), spacing scale.
- Landing/login page with real copy, no Lorem ipsum.

**App shell:** top bar (view switcher, prev/next/today, search, notifications, profile), left sidebar (mini-month, "My calendars" with color checkboxes, "Shared with me", create calendar).

**Interactions:**
- Click empty slot → quick-create popover (title, time, calendar), with "More options" opening the full editor
- Click event → detail popover with edit/delete
- Drag to move, drag edge to resize, with optimistic UI and rollback on error
- Recurring edit/delete dialog: "This event / This and following / All events"

**States:** skeleton loaders, empty states with illustrations/CTAs, error boundary and friendly error messages (map API error codes → copy), toasts, confirmation dialogs (no `alert()` anywhere).

**Responsive:** desktop primary; tablet collapses the sidebar; mobile defaults to Day/agenda view with a bottom action button.

**Bonus picks (only after core is done):** dark mode, agenda view, keyboard shortcuts (`c`, `t`, `j/k`, `/`), `.ics` export, conflict highlighting. Drag-and-drop and optimistic UI come with the core.

---

## 8. Phased delivery plan

Each milestone below is a stopping point where we can work through it together. **Do not start the next milestone until the current one's "Done when" is true.**

### M0: Foundations and decisions
- [x] Create monorepo: `backend/`, `frontend/`, `docs/`, `.env.example`, `.gitignore`
- [x] Spring Boot skeleton, single environment, config via env vars / `.env`, Flyway baseline
- [x] Global exception handler with a consistent error JSON (`code`, `message`, `details`, `traceId`), structured logging with correlation id
- [x] CI (GitHub Actions): build + test
- [x] Start `AI_USAGE.md` running log
- [x] Write ADR-001 (stack) and ADR-002 (time model)
- **Done when:** `./mvnw spring-boot:run` serves `/healthz` and Swagger UI against deployed cloud DB.

### M1: Auth and users
- [x] Sign up, login, logout, refresh, `GET/PATCH /me` (including IANA time zone)
- [x] Stateless Spring Security, BCrypt password hashing, validation, token rotation
- [x] Domain event hook for default calendar provisioning on sign-up (`UserRegisteredEvent`)
- [x] Tests: sign-up validation, duplicate email 409, bad credentials 401, token rotation, protected route 401/200, time zone update
- **Done when:** protected endpoint rejects anonymous calls (401) and works with a valid token (200).

### M2: Calendars, sharing, AccessPolicy
- [x] Calendar CRUD, enable/disable (per-user prefs), default calendar rules
- [x] Share/unshare by email (must be a registered user), VIEW/EDIT, list "shared with me"
- [x] `AccessPolicy` central authorization gate + full permission-matrix tests
- **Done when:** the matrix in section 4.3 is covered by passing tests (zero existence leaks with 404, explicit 403 for unauthorized modifications).

### M3: Events (non-recurring) + search + conflicts
- [x] Event CRUD, validation (end after start, all-day rules), optimistic locking (409 on stale version)
- [x] Range query with pagination limits, `pg_trgm` search over title/description/location (scoped to accessible calendars)
- [x] `AvailabilityService` (overlap rule: `startA < endB AND endA > startB`)
- [x] Reminder config on events
- [x] Tests: create/update, validation, search scoping, conflicts including edge-touching ranges
- **Done when:** the REST API supports the full non-recurring workflow, documented in Swagger.

### M4: Recurrence
- [x] RRULE subset validation, `RecurrenceExpander`, range expansion
- [x] Exceptions: single-occurrence edit/cancel, this-and-following split, all
- [x] Recurrence-aware search results and availability
- [x] Tests: weekly/monthly/yearly edge cases (31st, Feb 29), DST, exceptions, UNTIL/COUNT
- **Done when:** the standup-every-Monday example works, including editing and deleting one occurrence.

### M5: Reminders and notifications
- [x] Scheduler, dispatches (idempotent), `NotificationChannel` abstraction, in-app channel
- [x] Notifications API (list, mark read)
- [x] Tests: due calculation, recurring occurrence, no duplicate sends
- **Done when:** a reminder creates exactly one notification at the right time.

### M6: Frontend foundation
- [x] Branding tokens, logo, favicon, landing/login/signup, app shell, protected routes
- [x] API client (JSDoc-documented helpers), auth refresh handling, global error mapping from the backend `code` field
- **Done when:** a user can sign up, log in, and land in an empty branded calendar.

### M7: Calendar experience
- [x] Month/Week/Day with Prev/Next/Today, slot-click create, event popover, full editor
- [x] Drag/resize with optimistic UI, recurring edit/delete dialogs
- [x] Sidebar: calendars, toggles, create/rename/delete, sharing dialog, shared calendars
- [x] Search UI, notification bell, settings (time zone, tokens)
- [x] Loading/empty/error states, confirmation dialogs
- **Done when:** the full reviewer path works in the browser on desktop.

### M8: MCP server
- [x] PAT management (API + UI), MCP auth filter
- [x] All 9 tools as thin adapters, AI-friendly descriptions and errors
- [x] Tests: each tool, wrong-user isolation, view-only share rejection, revoked token
- [x] Test with a real client (Claude Desktop/Claude Code/Cursor): "What's on my calendar tomorrow?", "Schedule a meeting with John tomorrow at 3 PM", "Am I free Friday 2-5 PM?"
- **Done when:** the three prompts above work end-to-end and appear in the web UI.

### M9: Deployment
- [x] Multi-stage Dockerfile, prod profile, env vars, DB migrations on boot
- [x] Seed script: demo account(s), a second user for sharing, sample events, a recurring event
- [x] Separate frontend (Static Site) and backend (Web Service) deployment configuration (`render.yaml`)
- [ ] Push to GitHub and deploy to Render, verify HTTPS & health checks
- [ ] Smoke-test the **public** URL, including the MCP endpoint
- **Done when:** a fresh browser session completes the reviewer path on the live URL.

### M10: Polish, docs, bonus
- [ ] Responsive pass (desktop, tablet, mobile), spacing/typography audit, the Polish Checklist from the brief
- [ ] Bonus picks: dark mode, agenda view, keyboard shortcuts, `.ics` export
- [ ] README (overview, architecture diagram, local dev, env vars, deployment, MCP instructions, known limitations)
- [ ] `docs/MCP_AUTH.md`, ADRs, `AI_USAGE.md` finalized
- [ ] Review rehearsal (section 10)
- **Done when:** the submission checklist (section 9) is all ticked.

**Suggested time split** (adjust once you know the deadline): M0-M1 10%, M2-M3 20%, M4 15%, M5 5%, M6-M7 25%, M8 10%, M9 5%, M10 10%. If time is short, cut order is: bonus features → mobile polish extras → SMS/email stubs, **never** tests, MCP auth, or deployment.

---

## 9. Submission checklist

- [ ] Git repo (clean history, meaningful commits, no secrets, `.env.example` only)
- [ ] Production URL
- [ ] MCP server URL + configuration snippets
- [ ] Demo account credentials (plus a second account to demonstrate sharing)
- [ ] README with architecture diagram
- [ ] `AI_USAGE.md` (tools used, what was generated, what you changed, notable prompts)
- [ ] Swagger/OpenAPI reachable on the deployed app
- [ ] Tests passing in CI
- [ ] Walk the reviewer path once on the live site in an incognito window

---

## 10. Review preparation (live questions to expect)

Be ready to explain **and modify** live:
- Why `timestamptz` for one-offs but local time plus zone for recurring? What happens on a DST change?
- How does an occurrence get edited or deleted without duplicating the series?
- Walk through the ERD, then explain the indexes and the search approach.
- Where exactly is authorization enforced? How do you know the MCP layer cannot bypass it?
- How would you move reminders to email or SMS? How do you prevent duplicate sends with two instances running?
- What breaks first at 100x scale? (Recurrence expansion cost, search, reminder polling. Know the answer for each.)
- Debug exercise: a stale update returns 409. Why, and how does the client recover?

**Rule for AI use:** generate with AI, but read every line of auth, recurrence, and authorization code, and write your own tests for them. Log what you changed in `AI_USAGE.md` as you go, not at the end.

---

## 11. Open questions to settle before M0

1. Deadline? (Sets the time split above.)
2. ~~Frontend preference~~ Decided: React (JavaScript) + Vite.
3. Preferred hosting account (Render, Railway, Fly.io, AWS/GCP)? Since you work on GCP daily, Cloud Run + Cloud SQL is a strong alternative, but costs more setup.
4. ~~Product name~~ Decided: **Zoner**.

---

## 12. Zero-cost constraints and mitigations

Everything in this plan can run at ₹0, but the free tiers shape a few design choices. Verify current limits on each provider's pricing page before deploying, as they change.

| Piece | Free option | Limit to design around |
|---|---|---|
| Source + CI | GitHub (public repo + Actions) | None that matters here |
| App hosting | Render free web service | Sleeps after ~15 min idle, 30-60 s cold start, 512 MB RAM, 0.1 CPU, no persistent disk, outbound SMTP blocked |
| Database | Neon free Postgres | 0.5 GB storage, 100 compute-hours/month, scale-to-zero (first query after idle is slower) |
| Domain | `*.onrender.com` | Custom domain not needed |
| Everything else | Spring Boot, FullCalendar core, Tailwind, React, springdoc, Testcontainers, MCP SDK | All open source |

Design consequences:
1. **JVM on 512 MB:** set `-Xmx` around 300-350m, use `-XX:+UseSerialGC`, lazy bean init, small connection pool (max ~5), no heavy libraries. Test the container with a 512 MB memory limit locally before deploying.
2. **Cold starts:** `/healthz` endpoint, a friendly "waking up the server" state in the UI, and a free uptime pinger (e.g. UptimeRobot) as a best-effort keep-warm. Warm the app before sharing the URL.
3. **Reminders while asleep:** a sleeping instance cannot run the scheduler. The dispatcher must be **catch-up safe**: on startup and on each tick, process every reminder with `fire_at <= now` and no dispatch row yet (idempotent via the unique constraint). Document this in the README.
4. **Email:** SMTP is blocked on Render free, so the email channel stays a stub (as the brief allows). Do not promise real email.
5. **Neon connections:** use the pooled connection string, handle the first-query wake-up with a retry/timeout setting, and keep seed data small.
6. **Free-tier risk:** providers can change or suspend free plans. Keep the Dockerfile and `docker-compose.yml` portable and note a fallback host in the README.
