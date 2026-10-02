# Zoner: Architecture & Engineering Decisions (Scratch to End)

This document provides a comprehensive record of all foundational architectural, technical, and engineering decisions made during the design and implementation of **Zoner**.

Every decision documented below reflects real-world production trade-offs, security postures, time-zone intricacies, and operational realities designed to deliver a resilient, zero-cost, enterprise-grade calendar platform.

---

## Decision Index

1. [ADR-001: Technology Stack & Language Selection](#1-technology-stack--language-selection)
2. [ADR-002: Relational Schema Ownership & Flyway Migrations](#2-relational-schema-ownership--flyway-migrations)
3. [ADR-003: Time Model & IANA Time Zones (Instant vs. Wall-Clock)](#3-time-model--iana-time-zones-instant-vs-wall-clock)
4. [ADR-004: Recurrence Engine Architecture (Query-Time Expansion vs. Row Materialization)](#4-recurrence-engine-architecture-query-time-expansion-vs-row-materialization)
5. [ADR-005: Recurrence Exception & Series Splitting Model](#5-recurrence-exception--series-splitting-model)
6. [ADR-006: Centralized AccessPolicy & Anti-Enumeration Security](#6-centralized-accesspolicy--anti-enumeration-security)
7. [ADR-007: Embedded Model Context Protocol (MCP) Server Architecture](#7-embedded-model-context-protocol-mcp-server-architecture)
8. [ADR-008: Personal Access Token (PAT) Authentication for AI Agents](#8-personal-access-token-pat-authentication-for-ai-agents)
9. [ADR-009: Reminder Dispatch Engine & Idempotency Guarantee](#9-reminder-dispatch-engine--idempotency-guarantee)
10. [ADR-010: Zero-Cost Separated Production Deployment Architecture](#10-zero-cost-separated-production-deployment-architecture)

---

## 1. Technology Stack & Language Selection

### Context
The project required a robust calendar platform with complex business logic (time zone shifts, recurrence rules, access matrices, and conflict detection), an MCP server for AI integrations, and zero-cost public deployment.

### Decision
* **Backend:** Java 21 LTS with Spring Boot 3.5.x, Spring Data JPA, Spring Security, and Maven.
* **Database:** PostgreSQL 16 hosted on Neon Serverless Postgres.
* **Frontend:** React 18, Vite 6, Tailwind CSS, Lucide Icons, and FullCalendar (Core + DayGrid + TimeGrid + Interaction).

### Rationale & Trade-offs
* **Why Java 21 & Spring Boot 3.5:** Java 21 offers modern language enhancements (records as immutable DTOs, pattern matching, enhanced switch statements, and virtual thread readiness). Spring Boot 3.5 provides battle-tested ecosystem stability with `springdoc-openapi` 2.8, Spring Security 6, and Testcontainers.
* **Why React + Vite (SPA) instead of Next.js / SSR:** A pure Single Page Application (SPA) deployed to a static CDN edge eliminates server runtime costs, isolates frontend crashes from API failures, and enables ultra-fast hot module replacement (HMR in ~100 ms) during development.
* **Why FullCalendar:** FullCalendar is the industry benchmark for drag-and-drop, slot-selection, and multi-view grid rendering. Using FullCalendar for the rendering layer allowed development effort to focus entirely on difficult domain problems: time zones, recurrence exceptions, MCP tools, and access policies.

---

## 2. Relational Schema Ownership & Flyway Migrations

### Context
Many applications rely on Hibernate's `ddl-auto: update` to generate schemas automatically. However, in multi-calendar, multi-tenant relational schemas, automatic schema generation leads to uncontrolled index creation, inconsistent data types, and non-reproducible database states across environments.

### Decision
* The database schema is **100% owned by Flyway SQL migrations** located in `backend/src/main/resources/db/migration/`.
* Hibernate is explicitly configured to `ddl-auto: validate`. The application refuses to start if the JPA entities deviate from the applied SQL migrations.

### Migration Sequence
1. `V1__init_schema.sql`: Initial extensions and baseline.
2. `V2__auth_and_users.sql`: `users` and `refresh_tokens` tables with case-insensitive unique email index (`citext` or `LOWER(email)`).
3. `V3__calendars_and_shares.sql`: `calendars`, `calendar_shares`, and `user_calendar_prefs` with foreign keys and cascade rules.
4. `V4__events.sql`: `events` and `reminders` tables with UTC timestamp indices.
5. `V5__event_exceptions.sql`: `event_exceptions` table for recurrence overrides and cancellations.
6. `V6__reminders_and_notifications.sql`: `reminder_dispatches` and `notifications` tables with composite unique constraints.
7. `V7__personal_access_tokens.sql`: `personal_access_tokens` table for MCP authentication.

---

## 3. Time Model & IANA Time Zones (Instant vs. Wall-Clock)

### Context
Time handling is where calendar applications fail most frequently:
* A team in London schedules an event at 10:00 AM Europe/London. When the UK transitions between GMT and British Summer Time (BST), does the meeting stay at 10:00 AM local time or move to 9:00 AM?
* How should one-off vs. recurring events be stored in PostgreSQL?

### Decision
1. **One-Off Events (Stored as UTC Instants):**
   * Stored in PostgreSQL as `timestamptz` (`start_at` and `end_at`).
   * The event's original `time_zone` is preserved for rendering context and editing.
2. **Recurring Events (Stored as Wall-Clock + IANA Time Zone):**
   * Stored as local date-time strings or wall-clock components (`local_start_time`, `local_end_time`) paired with an IANA time zone identifier (e.g. `America/New_York`).
   * Each occurrence is dynamically projected into the target time zone using `java.time.ZoneId` rules.
   * **Result:** A recurring weekly standup scheduled for 10:00 AM EDT stays at 10:00 AM EST when Daylight Saving Time ends.
3. **All-Day Events:**
   * Handled as pure calendar dates (`is_all_day = true`), independent of time zones, preventing them from shifting dates when viewed across the international date line.
4. **Deterministic Time Testing:**
   * Services never call `Instant.now()` or `LocalDate.now()` directly. Instead, a `java.time.Clock` bean is injected, allowing unit tests to simulate any arbitrary point in time or DST boundary deterministically.

---

## 4. Recurrence Engine Architecture (Query-Time Expansion vs. Row Materialization)

### Context
When a user creates a daily recurring event for 1 year, two architectural approaches exist:
1. **Materialization:** Insert 365 individual rows into the `events` table.
2. **Query-Time Expansion:** Store 1 master event row with an RFC 5545 `RRULE` string, and expand occurrences in memory when queried.

### Decision
* We chose **Query-Time Expansion** using RFC 5545 `RRULE` strings and the `lib-recur` expansion library.

### Rationale & Trade-offs
| Metric | Row Materialization | Query-Time Expansion (Zoner) |
|---|---|---|
| **Database Storage** | Explodes ($O(N)$ rows for every recurring series) | Minimal ($O(1)$ master row per series) |
| **Updating Series** | Requires updating hundreds of rows in a heavy transaction | Single update on master row |
| **Infinite Series** | Impossible (cannot store infinite rows) | Supported seamlessly (`RRULE` with no `COUNT` or `UNTIL`) |
| **Query Cost** | Simple range query | Range query on masters + in-memory expansion |

By constraining event range queries to active viewport windows (e.g. 1 month or 1 week) and caching parsed rules, query-time expansion scales cleanly without bloating database storage.

---

## 5. Recurrence Exception & Series Splitting Model

### Context
A user needs to modify a single occurrence (e.g., move Tuesday's standup by 1 hour), cancel a single meeting for a holiday, or change the location for all future meetings.

### Decision
Zoner implements the industry-standard 3-tier recurrence modification model:

1. **`THIS` (Single Occurrence Modification / Cancellation):**
   * The master event is **not** duplicated or split.
   * An entry is inserted into the `event_exceptions` table with `original_start_at` pointing to the exact occurrence being modified.
   * If cancelled: `is_cancelled = true`.
   * If rescheduled/edited: Custom `start_at`, `end_at`, `title`, or `location` overrides are stored.
   * During query expansion, the exception is merged over the computed occurrence.
2. **`THIS_AND_FOLLOWING` (Series Split):**
   * The original master event's `RRULE` is truncated with an `UNTIL` clause set to the day before the split date.
   * A new master recurring event is inserted starting on the split date with the updated parameters.
   * Past historical meetings remain untouched.
3. **`ALL` (Master Series Update):**
   * The root `events` row is updated directly.

---

## 6. Centralized AccessPolicy & Anti-Enumeration Security

### Context
Security and authorization must be uniform regardless of whether a request originates from the Web UI (REST API) or an AI agent (MCP server). Furthermore, authorization checks must not leak the existence of private resources to unauthorized users.

### Decision
1. **Single Source of Truth (`AccessPolicy`):**
   * A dedicated `AccessPolicy` component encapsulates all permission checks across calendars, events, shares, and reminders.
   * Both REST controllers and MCP tool adapters pass through this single policy layer before any database mutation or read.
2. **Anti-Enumeration Protection (404 vs 403):**
   * If User A attempts to view or edit an event belonging to User B, and User B has not shared that calendar, the application returns **`404 Not Found`** (not `403 Forbidden`).
   * **Security Rationale:** Returning `403 Forbidden` confirms that an event or calendar with that ID exists in the database. Returning `404 Not Found` prevents attackers from discovering private IDs through automated ID scanning.
3. **Explicit 403 for Read-Only Collaborators:**
   * If User A has `VIEW` permission on User B's shared calendar, User A *knows* the calendar exists. Therefore, if User A attempts to create, update, or delete an event on that calendar, the system explicitly returns **`403 Forbidden`** with a clear message: *"You only have read-only access to this shared calendar."*

---

## 7. Embedded Model Context Protocol (MCP) Server Architecture

### Context
The Model Context Protocol (MCP) by Anthropic establishes an open standard for AI models to discover and execute tools. The architectural question was: should the MCP server be a separate standalone Node.js/Python process, or embedded within the Spring Boot application?

### Decision
* The MCP server is **embedded directly inside the Spring Boot backend** as a dedicated controller (`McpController`) and service (`McpService`).
* It supports both **Streamable HTTP JSON-RPC 2.0** (`POST /api/mcp`) and **Server-Sent Events (SSE)** (`GET /api/mcp/sse`).

### Rationale & Trade-offs
* **Shared Service Layer:** The MCP controller calls the exact same `EventService`, `CalendarService`, and `AccessPolicy` beans as the web REST controllers.
* **Zero Permission Drift:** An AI agent cannot bypass business rules, create overlapping meetings without conflict alerts, or edit read-only calendars, because the exact same validation logic runs for both human and AI users.
* **Single Deployment:** Eliminates the need to maintain, deploy, and monitor a separate microservice.

---

## 8. Personal Access Token (PAT) Authentication for AI Agents

### Context
Web browsers authenticate via short-lived JWT access tokens (15-minute expiration) and rotating refresh tokens. However, AI clients such as Claude Desktop or Cursor run as background processes on the user's desktop and cannot perform interactive browser logins or cookie exchanges.

### Decision
* Implemented **Personal Access Tokens (PATs)** conforming to standard API token security practices.

### Security Implementation
* **Format:** 32 bytes of cryptographically secure random entropy formatted as 64 hex characters with the prefix `zoner_pat_` (e.g., `zoner_pat_4f2b91c...`).
* **One-Way Hashing:** The database **never stores plaintext tokens**. Tokens are hashed with SHA-256 before storage: `SHA-256("zoner_pat_...") -> 64-char hex digest`.
* **One-Time Display:** The plaintext token is returned to the user exactly once upon creation.
* **Prefix Masking:** The database stores a `token_prefix` (e.g. `zoner_pat_4f2b...`) so the user can identify their tokens in the UI without revealing the secret.
* **Revocation & Expiration:** Users can revoke tokens at any time, or configure expiration windows (30 days, 90 days, 1 year).
* **Multi-Token Filter:** `JwtAuthenticationFilter` inspects the `Authorization: Bearer` header: if it starts with `zoner_pat_`, it authenticates via `TokenService`; otherwise, it validates the JWT.

---

## 9. Reminder Dispatch Engine & Idempotency Guarantee

### Context
A recurring background scheduler periodically queries for reminders that have reached their due time (`fire_at <= now`). In distributed environments or during application restarts, the scheduler may execute multiple times concurrently or re-process the same window.

### Decision
1. **Strict Database-Enforced Idempotency:**
   * The `reminder_dispatches` table features a composite unique constraint: `UNIQUE (reminder_id, occurrence_start)`.
   * When the dispatcher triggers, it checks for existing dispatch records. Even if two concurrent scheduler threads attempt to dispatch the same reminder simultaneously, the database unique constraint prevents duplicate rows and notifications.
2. **Cold-Start Catch-Up Safety:**
   * On Render's free tier, the application sleeps after 15 minutes of inactivity. While asleep, scheduled cron tasks do not execute.
   * The dispatcher incorporates a rolling lookback window: when the server wakes up, it evaluates all un-dispatched reminders whose `fire_at` occurred during the sleep period and delivers them immediately.

---

## 10. Zero-Cost Separated Production Deployment Architecture

### Context
The goal was to deploy a production-grade environment at **₹0 / $0 monthly cost** while maintaining high availability, independent CI/CD pipelines, and clean separation of concerns.

### Architecture
```
┌──────────────────────────────────────────────┐
│  Render Static Site                          │
│  zoner-6uv2.onrender.com                     │
│  - React 18 + Vite Production Build          │
│  - SPA Routing: /* -> /index.html            │
│  - Global Edge CDN Caching                   │
└──────────────────────┬───────────────────────┘
                       │ HTTPS / JSON-RPC
                       ▼
┌──────────────────────────────────────────────┐
│  Render Web Service (Docker Container)       │
│  zoner-backend.onrender.com                  │
│  - Java 21 Alpine Multi-Stage Dockerfile     │
│  - JVM Tuning: -Xmx350m -XX:+UseSerialGC     │
│  - Flyway Migrations on Boot                 │
│  - Streamable HTTP & SSE MCP Server          │
└──────────────────────┬───────────────────────┘
                       │ TLS 1.3 / Pooled Connection
                       ▼
┌──────────────────────────────────────────────┐
│  Neon Serverless PostgreSQL 16               │
│  - Auto-scaling compute                      │
│  - Automated daily backups                   │
└──────────────────────────────────────────────┘
```

### Design Decisions for Free-Tier Execution
1. **JVM Memory Tuning under 512 MB:**
   * Render Free Web Services provide 512 MB RAM. Standard JVM configurations often exceed this limit during garbage collection.
   * Configured JVM arguments in `backend/Dockerfile`:
     `-Xms128m -Xmx350m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m`
   * Single-threaded Serial GC minimizes memory overhead, guaranteeing the container never encounters Out-Of-Memory (OOM) kills.
2. **Render Blueprint (`render.yaml`):**
   * Configured infrastructure as code (IaC) defining the backend web service, environment variables, health check paths (`/healthz`), and frontend static site with SPA redirect rules.
3. **Automated Seed Script (`DataSeeder`):**
   * An idempotent `CommandLineRunner` automatically seeds the reviewer demo accounts and sample recurring calendars on initial startup, ensuring immediate evaluability.
