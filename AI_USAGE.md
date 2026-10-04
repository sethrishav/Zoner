# AI Usage & Human Engineering Oversight Log

> **Project:** Zoner — Time-Zone Intelligent Calendar & Model Context Protocol Platform  
> **Author:** Rishav Sharma  
> **Repository:** [https://github.com/rishavsair/Zoner](https://github.com/rishavsair/Zoner)  
> **Live Web Application:** [https://zoner-6uv2.onrender.com](https://zoner-6uv2.onrender.com)  

---

## 1. Tooling Ecosystem & Roles (Where to Start)

To understand how Zoner was planned, built, tested, and verified, here is the complete toolchain, the sequential workflow, and the role each tool played:

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                     HUMAN SOFTWARE ENGINEER                                      │
│           (Architectural Direction, Plan Review & Modification, Line-by-Line Code Review)        │
└────────▲───────────────────────────────▲───────────────────────────────────────────▲─────────────┘
         │                               │                                           │
 1. Requests Initial Plan        2. Modifies Plan & Directs Execution        4. Verifies via External AI
         │                               │                                           │
         ▼                               ▼                                           ▼
┌──────────────────┐          ┌──────────────────────────────────┐        ┌────────────────────────┐
│  CLAUDE (SONNET) │          │      GOOGLE ANTIGRAVITY IDE      │        │ CLAUDE DESKTOP & CURSOR│
│ (Plan Generator) │          │          (Plan Executor)         │        │   (MCP Verification)   │
│                  │          │                                  │        │                        │
│ • Generated the  │          │ • Autonomous code writing        │        │ • External agent tests │
│   original       │─────────>│ • Spring Boot backend code       │        │ • JSON-RPC tool calls  │
│   architecture   │  Human   │ • React 18 UI components         │        │ • Checks availability  │
│   plan (PLAN.md) │ Modified │ • Terminal builds & unit tests   │        │ • Books test meetings  │
│ • Initial schema │   Plan   │ • Flyway migrations execution    │        │ • Verifies PAT auth    │
│   proposals      │          │ • Real-time test loop            │        │ • Validates SSE streams│
└──────────────────┘          └────────────────┬─────────────────┘        └───────────▲────────────┘
                                               │                                      │
                                               ▼ Writes Code & Migrations             │ Calls /api/mcp
                              ┌───────────────────────────────────────────────────────┴────────────┐
                              │                         ZONER PLATFORM                             │
                              │        Spring Boot 3 + React 18 + Embedded MCP Server              │
                              └────────────────────────────────────────────────────────────────────┘
```

### Tool Roles & Scope:

| Tool | Core Role | How It Was Used in Zoner |
|---|---|---|
| **Claude (Anthropic Claude 3.7 Sonnet)** | **Initial Architecture Plan Generator** | Used upfront to draft the foundational engineering blueprint (`PLAN.md`), outlining the initial REST endpoints, database schema drafts, milestone phases, and technical requirements. |
| **Human Software Engineer** | **Architectural Ownership & Plan Modification** | Critiqued and modified Claude's initial plan (changing the recurrence model, switching to monorepo domain packaging, decoupling reminder locks, adding anti-enumeration security), reviewed all git diffs line by line, caught subtle ORM proxy bugs, rewrote flawed LLM logic, tested edge cases in the browser, and verified production deployments. |
| **Google Antigravity IDE** | **Autonomous Plan Executor & Code Writing Agent** | Served as the primary agentic development environment to execute the human-refined plan. Antigravity handled file generation, repetitive boilerplate, class scaffolding, terminal execution (running Maven builds, Flyway migrations, and Vite builds), and running automated unit tests. Every block of code written by Antigravity was monitored, tested, and reviewed by the engineer in real time. |
| **Claude Desktop & Cursor** | **MCP Integration Testing & External Client Verification** | Used as external AI client applications to test and verify the embedded Model Context Protocol server (`/api/mcp`). Connected using generated Personal Access Tokens (`zoner_pat_...`) to simulate real-world AI agent usage: discovering tools, checking mutual free availability, and scheduling meetings without browser UI. |

---

## 2. Human-in-the-Loop Philosophy & Review Process

AI was used throughout the development of Zoner to accelerate implementation, but **the human engineer remained in the driver's seat at all times.**

LLMs excel at boilerplate and syntax generation, but when left unsupervised on complex distributed systems, they frequently propose naive or broken implementations:
- Pre-generating thousands of recurring rows in the database.
- Writing detached Hibernate collection traversals that crash with `LazyInitializationException`.
- Committing reminder dispatch locks before confirming delivery, causing permanently lost notifications.
- Leaking private resource IDs by returning `403 Forbidden` instead of `404 Not Found`.
- Checking `{ unreadCount: N } > 0` directly in JavaScript (which evaluates to `false` and breaks UI badges).

### The Continuous Review Loop:
Every single time code was written with AI assistance:
1. **Interactive Compilation & Testing:** Code was compiled immediately, and tests were run against local and containerized PostgreSQL instances.
2. **Line-by-Line Code Review:** The human engineer audited git diffs for architectural purity, memory allocation, concurrency safety, and transaction boundaries.
3. **Manual Browser & API Verification:** Features were tested interactively in the browser (creating recurring events, editing single instances, toggling time zones, and observing reminder popups).
4. **Active Logic Correction:** Whenever the AI's implementation took shortcuts or produced subtle bugs, **direct human intervention stepped in to rewrite the logic**.

---

## 3. Architecture Plan: Built by Claude, Modified by Human, Executed through Antigravity

Development followed a structured 3-stage progression:
1. **Original Plan Built by Claude:** Claude 3.7 Sonnet generated the initial full-stack technical roadmap (`PLAN.md`) with schemas, endpoints, and milestones.
2. **Human Intervention on the Plan:** The human engineer reviewed Claude's plan, identified critical real-world flaws, and substantially altered the architecture before writing code.
3. **Execution Through Google Antigravity:** The refined plan was fed into Google Antigravity IDE, which executed the plan by writing the code, generating migrations, running terminal builds, and executing test suites under active human supervision.

```
[ Claude: Drafted Initial Plan ]
                │
                ▼
[ Human Engineer: Substantial Architectural Modifications ]
                │
                ▼
[ Google Antigravity IDE: Autonomous Code Writing & Execution ]
                │
                ▼
[ Claude Desktop & Cursor: External MCP Verification ]
```

### Key Human Modifications Made to Claude's Initial Plan:

#### 3.1. Monorepo Structuring & Domain Packaging
- **Claude's Initial Proposal:** Generating separate independent repositories or dumping backend logic into generic flat layers (`controllers/`, `models/`, `services/`).
- **Human Modification:**
  - Consolidated the entire project into a unified **monorepo** (`backend/` + `frontend/`), linked by a single `render.yaml` deployment manifest. This guaranteed atomic commits across database migrations, DTOs, and React UI components.
  - Enforced **domain-driven package organization** in Spring Boot (`auth/`, `calendar/`, `event/`, `reminder/`, `mcp/`, `common/`, `config/`). All classes related to a domain concept live together, ensuring tight encapsulation and clear aggregate roots.

#### 3.2. Dynamic Recurrence Engine (RFC 5545) vs. Database Row Explosion
- **Claude's Initial Proposal:** Pre-generating individual database rows for recurring events (e.g., writing 365 rows for a daily meeting).
- **Human Modification:**
  - Rejected pre-generation. Mandated storing a single canonical master event with an RFC 5545 `RRULE` string.
  - Implemented dynamic bounding-box expansion on query demand via `RecurrenceExpander`.
  - Structured an isolated `event_exceptions` delta table supporting `THIS` (single occurrence override), `THIS_AND_FOLLOWING` (series truncation and forward split), and `ALL` (master series edit).

#### 3.3. Three-Tier Reminder Architecture (Decoupling Locks from Inboxes)
- **Claude's Initial Proposal:** Tracking reminder delivery status via a simple boolean flag on the event record, or checking whether a notification exists in the user inbox table.
- **Human Modification:**
  - Separated concerns into three strictly isolated database tables:
    1. `reminders`: User intent / configuration rule.
    2. `reminder_dispatches`: System-level idempotency lock ensuring an occurrence fires exactly once across server restarts.
    3. `notifications`: Personal user inbox, which users can read and delete freely without breaking scheduler idempotency.

#### 3.4. Dual Ingress & Anti-Enumeration Security
- **Claude's Initial Proposal:** Building a web-only API and using an external standalone proxy or bot for AI integration. Returning standard `403 Forbidden` on unauthorized resources.
- **Human Modification:**
  - Embedded the Model Context Protocol (MCP) server directly inside Spring Boot (`/api/mcp`), ensuring AI agents and web clients share the identical domain core, transactions, and security policies.
  - Introduced an `AccessPolicy` layer that returns uniform `404 Not Found` instead of `403 Forbidden` for inaccessible calendars, preventing attackers from discovering existing private IDs through enumeration.

---

## 4. Key Human-in-the-Loop Interventions & Logic Rewrites

Below are concrete, high-impact examples where AI-generated code produced subtle flaws, race conditions, or incorrect assumptions, and **human engineering intervention stepped in to rewrite the logic**:

### 1. Deferred Dispatch Commitment (Preventing Ghost Locks)
* **The AI Flaw:** When a reminder triggered, the AI-generated code wrote a `SENT` record into `reminder_dispatches` upfront to lock the occurrence, then attempted to resolve recipients and send notifications. If recipient resolution or message delivery failed halfway through (e.g., transient network drop or detached collection error), the reminder was marked `SENT`, the user never received an alert, and subsequent scheduler runs permanently skipped the event due to the existing lock record.
* **Human Intervention:** Inverted the execution pipeline in `ReminderDispatcher.java`. Recipient resolution and notification delivery are executed *first*. Only upon verified, successful delivery (`sentCount > 0`) is the idempotent dispatch record committed to the database. If delivery fails, no dispatch record is saved, allowing the scheduler to safely retry on the next cycle.

### 2. Eliminating Hibernate Detached Session Traps (`LazyInitializationException`)
* **The AI Flaw:** Inside the `@Scheduled` background worker, the AI attempted to resolve event guests by calling `event.getAttendees()` on an event loaded earlier in the method. Because background scheduler threads execute outside an active HTTP request transaction boundary, navigating lazy collections threw `LazyInitializationException: could not initialize proxy - no Session`. Adding a naive HQL `JOIN FETCH` caused Hibernate to crash with `MultipleBagFetchException`.
* **Human Intervention:** Bypassed ORM proxy navigation entirely. Replaced collection dereferencing with an explicit, index-backed repository call: `eventAttendeeRepository.findByEventId(event.getId())`. This executes an isolated, stateless SQL query that is completely independent of the entity's proxy state.

### 3. Decoupling Strict Idempotency from Mutable User Inboxes
* **The AI Flaw:** To guard against delivery failures, the AI introduced a check that re-fired reminders if `reminder_dispatches` had a record but `notifications` had 0 rows for that event. When real users read and deleted notifications from their UI inbox (`DELETE /api/notifications/{id}`), the notification count became 0. The AI's check interpreted this as a delivery failure and repeatedly re-sent reminder notifications every 60 seconds.
* **Human Intervention:** Completely severed the connection between system dispatch records and user inboxes. The `reminder_dispatches` table is the immutable cluster-wide execution lock; the `notifications` table is the user's mutable inbox. Deleting an inbox message never affects dispatch history.

### 4. JavaScript Type-Safe Deserialization for UI Toasts & Badges
* **The AI Flaw:** On the frontend, the AI polled `GET /api/notifications/unread-count` and directly compared the response: `if (res > 0)`. Because Spring Boot returns a JSON object record (`{ unreadCount: N }`), in JavaScript `{ unreadCount: 1 } > 0` evaluated to `false`, silently suppressing the notification badge and floating toast popups.
* **Human Intervention:** Rewrote the client-side parsing logic with defensive, type-safe deserialization: `const count = res?.unreadCount ?? (typeof res === 'number' ? res : 0)`. Connected this to an animated pulsing emerald green indicator dot on the bell icon and built an auto-dismissing (3.5-second) floating alert toast on the top-right whenever a new reminder fires.

### 5. Preserving Wall-Clock Time Across Daylight Saving Shifts (DST)
* **The AI Flaw:** When recurring events were scheduled, the AI converted start times directly into UTC timestamps. For a 10:00 AM daily standup in New York, converting to a fixed UTC instant meant that when Daylight Saving Time changed (clocks shifted by 1 hour), the meeting would jump to 9:00 AM or 11:00 AM local time.
* **Human Intervention:** Enforced storing local wall-clock time (`10:00:00`) alongside the canonical IANA time zone (`America/New_York`). Recurrence instances are projected dynamically into UTC using `ZoneId`, guaranteeing that local meetings remain fixed at 10:00 AM year-round regardless of DST transitions.

### 6. Anti-Enumeration Security Hardening
* **The AI Flaw:** When a user requested a calendar ID they did not own, the AI defaulted to throwing `403 Forbidden`. This leaked information: an attacker could probe calendar IDs from 1 to 10,000 and identify which IDs belonged to real users based on whether the server returned 403 vs 404.
* **Human Intervention:** Refactored `AccessPolicy` to return `404 Not Found` for any calendar or event not accessible to the current user. To an unauthorized caller, private resources look identical to nonexistent resources, eliminating ID enumeration attacks.

### 7. Production JVM Tuning for Free-Tier Cloud Containers
* **The AI Flaw:** The AI generated standard Spring Boot Dockerfiles without memory or garbage collection tuning. When deployed to Render's 512 MB free tier, the JVM's default ergonomics allocated memory aggressively, triggering Out-Of-Memory (OOM) kernel kills on startup.
* **Human Intervention:** Tuned the container runtime in `backend/Dockerfile` with explicit flags:
  `-Xms128m -Xmx350m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m`. This capped heap and metaspace usage safely below 400 MB, ensuring rock-solid stability on free-tier containers.

---

## 5. Milestone Execution & Human Quality Gates

| Milestone | What AI Drafted / Scaffolded | Human Review, Refactoring & Testing | Quality Gates Passed |
|---|---|---|---|
| **M0: Project Setup & Probes** | Spring Boot 3.5 skeleton, Maven setup, Actuator `/healthz` endpoints. | Added `.github/workflows/ci.yml`, configured PostgreSQL connection pooling, verified HTTP 200 health probes. | Probes operational; clean baseline established. |
| **M1: Auth & Users** | User entity, Flyway `V2`, JWT service, login/register endpoints. | Enforced BCrypt password hashing, case-insensitive email indexes (`LOWER(email)`), IANA timezone validation via `ZoneId.of()`. | Tested duplicate email 409, invalid credentials 401, JWT issuance. |
| **M2: Calendars & Sharing** | Calendar entities, Flyway `V3`, sharing permissions. | Enforced `AccessPolicy` anti-enumeration matrix (404 for stranger requests, 403 only for mutations on VIEW-only shares), automatic "Personal" calendar provisioning. | Tested sharing permissions matrix and ID probing resistance. |
| **M3: Events & Availability** | Event entity, Flyway `V4`, event CRUD, conflict checking. | Implemented mathematical interval intersection ($S_1 < E_2 \land E_1 > S_2$), optimistic locking (`@Version`), and attendees invitation model with RSVP tracking. | Tested CRUD, overlap warnings, and RSVP lifecycle. |
| **M4: Recurrence Engine** | EventException entity, Flyway `V5`, RRULE integration. | Enforced dynamic bounding-box expansion, DST local wall-clock preservation, and 3-way series mutations (`THIS`, `THIS_AND_FOLLOWING`, `ALL`). | Tested recurring rules, DST shift boundaries, and exception splitting. |
| **M5: Reminders & Notifications** | ReminderDispatch entity, Flyway `V6`, scheduler cron. | Built 3-tier architecture, fixed detached collection `LazyInitializationException`, enforced deferred dispatch commit (`sentCount > 0`), decoupled user deletions from audit locks. | Automated tests: `ReminderDispatcherUnitTest` passing 100%. |
| **M6: Frontend Shell & Auth** | React 18 scaffold, Tailwind CSS, login/register pages. | Configured Node 22 runtime, centralized Axios client with Bearer token interceptor, implemented global `AuthContext` and route protection. | Vite build clean; dev server starts in ~100ms. |
| **M7: FullCalendar & Modals** | FullCalendar v6 wrapper, event creation and edit dialogs. | Added time-zone projection, debounced conflict warnings, recurring edit choice dialog, unread notification badge dot, and auto-dismissing floating toasts. | Full user reviewer workflow validated in browser. |
| **M8: Model Context Protocol (MCP)** | Personal Access Token entity (`V7`, `V8`), JSON-RPC tools. | Embedded MCP directly in Spring Boot (`/api/mcp`), implemented SHA-256 token hashing at rest, verified streamable HTTP & SSE, created Cursor and Claude Desktop configs. | Verified with Claude Desktop and Cursor; 13 tool unit tests pass. |
| **M9: Cloud Deployment** | Dockerfile, Render blueprint (`render.yaml`). | Tuned JVM heap flags (`-Xmx350m -XX:+UseSerialGC`), verified CORS regex for Render subdomains, verified static Vite production builds. | Live deployment operational on Render (Web + API). |
| **M10: Polish & Documentation** | Draft documentation and user guides. | Thoroughly reviewed and rewritten in natural, grounded developer English; structured complete monorepo guide in `ENGINEERING_README.md`. | Verified all documentation against live systems. |

---

## 6. Reflections on Working with AI in Senior Engineering

Using AI agents effectively in software engineering is not about blindly accepting generated code; it is about **active architectural direction and rigorous code review**.

1. **AI accelerates syntax; humans own invariants:** Antigravity allowed rapid scaffolding of repetitive boilerplate, but the fundamental invariants—universal UTC storage, non-materialized recurrence math, 3-tier idempotency, and anti-enumeration security—required human foresight.
2. **Real testing reveals subtle AI blind spots:** In-memory timers, detached ORM session proxy dereferencing, and naive equality checks often compile cleanly and pass simple happy-path checks, but fail under realistic distributed conditions. Rigorous unit testing and live browser verification were essential to expose and fix these edge cases.
3. **The Human Stays in the Driver's Seat:** High-quality software requires critical skepticism. By treating AI output as a draft subject to strict human review, refactoring, and verification, we achieved a production-grade, highly reliable calendaring platform.
