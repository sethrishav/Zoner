# Zoner

> **A time-zone intelligent calendar platform with recurrence, multi-calendar sharing, real-time conflict detection, and an embedded Model Context Protocol (MCP) server for AI assistants.**

> 📖 **Deep Dive Technical Whitepaper:** For architectural design thinking, upfront planning & iterative changes, and a full engineering breakdown, read the **[Engineering Architecture & Design Document (ENGINEERING_README.md)](./ENGINEERING_README.md)**.

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React 18](https://img.shields.io/badge/React-18-blue.svg)](https://react.dev/)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-red.svg)](https://flywaydb.org/)
[![MCP 2024-11-05](https://img.shields.io/badge/MCP-2024--11--05-purple.svg)](https://modelcontextprotocol.io/)
[![Deployment Render](https://img.shields.io/badge/Deploy-Render-black.svg)](https://render.com/)

---

## 1. Live Production Deployment & Reviewer Quick-Start

Zoner is fully deployed and accessible live on the internet at **₹0 / $0 monthly hosting cost**:

| Service | Live URL | Description |
|---|---|---|
| **Web Application** | [https://zoner-6uv2.onrender.com](https://zoner-6uv2.onrender.com) | Responsive React 18 SPA hosted on Render Static Site |
| **API Backend** | [https://zoner-backend.onrender.com](https://zoner-backend.onrender.com) | Containerized Spring Boot 3.5 Web Service on Java 21 |
| **Interactive Swagger UI** | [https://zoner-backend.onrender.com/swagger-ui.html](https://zoner-backend.onrender.com/swagger-ui.html) | OpenAPI 3.0 interactive endpoint explorer |
| **Health Check & Probes** | [https://zoner-backend.onrender.com/healthz](https://zoner-backend.onrender.com/healthz) | Liveness and database connectivity probe |
| **MCP Server Endpoint** | [https://zoner-backend.onrender.com/api/mcp](https://zoner-backend.onrender.com/api/mcp) | Streamable HTTP & SSE transport for Cursor / Claude |

### Pre-Seeded Reviewer Credentials
The cloud database is pre-seeded with sample calendars, events, and sharing rules:

* **Primary Demo Account (Reviewer):**
  * **Email:** `demo@zoner.app`
  * **Password:** `Password123!`
* **Collaborator Account:**
  * **Email:** `colleague@zoner.app`
  * **Password:** `Password123!`
  * *(Owns shared calendars "Project Alpha" and "Company Announcements")*

> [!NOTE]
> **Free-Tier Cold Start:** Render free-tier services spin down after 15 minutes of inactivity. If the service is waking up, the initial page load may take 30–50 seconds. Once awake, all interactions and API responses are immediate.

---

## 2. Architecture & System Topology

Zoner is engineered as an API-first platform where human users (via the React web UI) and AI agents (via the Model Context Protocol) interact with the exact same domain services, transaction boundaries, and access control policies:

```mermaid
flowchart TD
    subgraph Clients["Clients & Agents"]
        Web["React 18 SPA (Vite + Tailwind)<br>zoner-6uv2.onrender.com"]
        AI["AI Assistants (Cursor / Claude Desktop / Claude Code)"]
    end

    subgraph Backend["Spring Boot 3.5 Application (Java 21)<br>zoner-backend.onrender.com"]
        subgraph Ingress["Ingress & Authentication"]
            Cors["CORS Filter (*.onrender.com)"]
            Auth["JwtAuthenticationFilter (JWT & PAT Support)"]
            RestCtrl["REST Controllers (/api/**)"]
            McpCtrl["McpController (/api/mcp - HTTP & SSE)"]
        end

        subgraph Core["Core Application Services (Single Source of Truth)"]
            Access["AccessPolicy (Central Authorization & Anti-Enumeration)"]
            CalService["CalendarService"]
            EvtService["EventService & RecurrenceExpander"]
            AvailService["AvailabilityService (Conflict Checking)"]
            TokenService["TokenService (PAT Management)"]
            NotifService["NotificationService & ReminderDispatcher"]
        end

        subgraph Persistence["Data & Migrations"]
            Flyway["Flyway Database Migrations (V1 to V7)"]
            JPA["Spring Data JPA (Hibernate ddl-auto: validate)"]
        end
    end

    subgraph Storage["Cloud Managed Storage"]
        DB[("Neon Serverless PostgreSQL 16<br>Pooled SSL Connection")]
    end

    Web -->|HTTPS REST API / Bearer JWT| Cors
    AI -->|JSON-RPC 2.0 / Bearer PAT| Cors
    Cors --> Auth
    Auth --> RestCtrl
    Auth --> McpCtrl

    RestCtrl --> Access
    McpCtrl --> Access

    Access --> CalService
    Access --> EvtService
    Access --> AvailService
    Access --> TokenService
    Access --> NotifService

    CalService --> JPA
    EvtService --> JPA
    AvailService --> JPA
    TokenService --> JPA
    NotifService --> JPA

    Flyway -.->|Applies Migrations on Boot| DB
    JPA ===>|TLS 1.3 Queries| DB
```

---

## 3. Database Schema & Entity-Relationship Diagram (ERD)

The database schema is **100% managed by Flyway SQL migrations** (`V1__init_schema.sql` through `V7__personal_access_tokens.sql`) with strict relational integrity, composite unique constraints, and foreign key cascades:

```mermaid
erDiagram
    users ||--o{ refresh_tokens : owns
    users ||--o{ calendars : creates
    users ||--o{ calendar_shares : receives
    users ||--o{ user_calendar_prefs : configures
    users ||--o{ personal_access_tokens : generates
    users ||--o{ notifications : receives

    calendars ||--o{ calendar_shares : shares
    calendars ||--o{ user_calendar_prefs : has_prefs
    calendars ||--o{ events : contains

    events ||--o{ event_exceptions : has_exceptions
    events ||--o{ reminders : has_reminders
    reminders ||--o{ reminder_dispatches : tracks_dispatch

    users {
        bigint id PK
        varchar email UK
        varchar password_hash
        varchar display_name
        varchar time_zone
        timestamp created_at
    }

    calendars {
        bigint id PK
        bigint owner_id FK
        varchar name
        varchar description
        varchar color
        boolean is_default
    }

    calendar_shares {
        bigint id PK
        bigint calendar_id FK
        bigint user_id FK
        varchar permission "VIEW | EDIT"
    }

    events {
        bigint id PK
        bigint calendar_id FK
        varchar title
        varchar description
        varchar location
        varchar color
        boolean is_all_day
        timestamptz start_at
        timestamptz end_at
        varchar time_zone
        varchar rrule
        time local_start_time
        time local_end_time
        bigint version "Optimistic Lock"
    }

    event_exceptions {
        bigint id PK
        bigint master_event_id FK
        timestamptz original_start_at
        boolean is_cancelled
        timestamptz start_at
        timestamptz end_at
        varchar title
    }

    reminders {
        bigint id PK
        bigint event_id FK
        integer minutes_before
        varchar channel "IN_APP"
    }

    reminder_dispatches {
        bigint id PK
        bigint reminder_id FK
        timestamptz occurrence_start
        timestamptz dispatched_at
    }

    notifications {
        bigint id PK
        bigint user_id FK
        bigint event_id FK
        varchar title
        varchar message
        boolean is_read
        timestamptz created_at
    }

    personal_access_tokens {
        bigint id PK
        bigint user_id FK
        varchar name
        varchar token_prefix
        varchar token_hash UK
        varchar scopes
        timestamptz expires_at
        timestamptz last_used_at
        boolean revoked
    }
```

---

## 4. Key Feature Walkthrough

### 1. Time-Zone & DST Intelligence
* **Instant vs. Wall-Clock:** One-off events are stored as UTC instants (`timestamptz`). Recurring events preserve their local wall-clock time and IANA time zone (e.g. `America/New_York`).
* **DST Transitions:** A 10:00 AM daily standup stays at 10:00 AM local time across Daylight Saving Time transitions, preventing meeting shifts.
* **All-Day Events:** Pure calendar dates unaffected by UTC offsets.

### 2. Full RFC 5545 Recurrence & 3-Tier Modification
* **Query-Time Expansion:** Employs `lib-recur` to dynamically project recurrence occurrences across requested viewport ranges (`[timeMin, timeMax]`), keeping database storage at $O(1)$.
* **Three Modification Modes:**
  * `THIS`: Creates an `EventException` for that specific occurrence (reschedule or cancel) without duplicating or corrupting the series.
  * `THIS_AND_FOLLOWING`: Truncates the original series with `UNTIL` and splits a new series from that date forward.
  * `ALL`: Directly updates the root master event definition.

### 3. Granular Multi-Calendar Sharing & Anti-Enumeration Security
* **Sharing Permissions:** Calendars can be shared with individual users as `VIEW` (read-only) or `EDIT` (collaborative).
* **Anti-Enumeration Protection:** If User A requests an event on User B's unshared calendar, the server returns `404 Not Found` (never `403 Forbidden`). This prevents attackers from enumerating sequential IDs.
* **Default Calendar Invariance:** Every user is auto-provisioned a "Personal" calendar that cannot be deleted.

### 4. Real-Time Conflict Detection & Availability Checking
* When picking dates in the Event Modal, Zoner actively evaluates overlapping intervals (`startA < endB AND endA > startB`) across all visible calendars.
* An inline warning banner alerts users to scheduling conflicts before saving.

### 5. In-App Notifications & Cold-Start Safe Reminders
* Scheduled dispatcher worker queries due reminders and inserts notifications into the user's inbox.
* **Idempotency Guarantee:** Backed by PostgreSQL composite unique constraint `UNIQUE (reminder_id, occurrence_start)`. Duplicate notifications are physically impossible.
* **Rolling Lookback Window:** If the application was asleep on Render's free tier, it catches up on missed reminders immediately upon wake-up.

### 6. Global Search (`⌘K` / `Ctrl+K`)
* Debounced (300 ms) search modal indexing event titles, descriptions, and locations across all accessible calendars.

---

## 5. Bonus Features & Advanced Capabilities

Beyond the baseline calendaring requirements, Zoner implements a comprehensive suite of advanced and bonus capabilities from the optional project specification:

### Bonus Features Implementation Matrix

| Bonus Feature | Status | Description & Engineering Approach |
|---|:---:|---|
| **Drag-and-Drop Scheduling** | ✅ **Implemented** | Interactive event repositioning and edge-drag duration resizing across Day and Week views with optimistic visual updates and automatic rollback. |
| **Conflict Visualization** | ✅ **Implemented** | Real-time interval overlap calculation (`startA < endB AND endA > startB`) across all active calendars, featuring an inline amber warning banner in the event modal and the `check_availability` endpoint. |
| **Natural-Language Event Creation** | ✅ **Implemented (via MCP)** | Full conversational parsing via connected AI assistants (Claude, Cursor, Claude Code) leveraging the embedded Model Context Protocol server. |
| **Attendees and RSVP** | ✅ **Implemented** | First-class guest management backed by the `event_attendees` table with composite unique constraints, status tracking (`PENDING`, `ACCEPTED`, `TENTATIVE`, `DECLINED`), and color-coded UI badges. |
| **Email Notifications** | ✅ **Implemented** | Pluggable `EmailNotificationChannel` integrated into `NotificationChannelRegistry` alongside in-app alerts, complete with fallback persistence and reminder dispatcher execution. |
| **Dark Mode** | ✅ **Implemented** | Full-app Dark Mode powered by Tailwind CSS and `ThemeContext`, featuring system OS detection, manual toggle in the navigation bar, and `localStorage` persistence. |
| **Keyboard Shortcuts** | ✅ **Implemented** | Universal `⌘K` / `Ctrl+K` shortcut to instantly trigger the debounced full-text search palette across titles, descriptions, and locations. |
| **Audit History / Activity Log** | ✅ **Implemented** | Persistent audit records in `reminder_dispatches` (tracking exact fire timestamps and preventing duplicate sends), PAT security audit timestamps (`last_used_at`), and structured correlation logs. |
| **Optimistic UI** | ✅ **Implemented** | Zero-latency UI updates when moving/resizing events and toggling calendar preferences before backend API roundtrips finish, with graceful rollback on errors. |
| **AI Event Summaries** | ✅ **Implemented (via MCP)** | AI agents can inspect schedules via `list_events`, `get_event`, and `search_events` to deliver high-level executive summaries of daily or weekly commitments. |
| **AI "Find a Time"** | ✅ **Implemented (via MCP)** | AI assistants query free/busy windows using the `check_availability` MCP tool to propose conflict-free meeting slots. |
| *Import / Export .ics* | ⏳ *Roadmap* | Standard RFC 5545 `.ics` file ingestion and download is queued for future milestones. |
| *Google Calendar Import* | ⏳ *Roadmap* | Third-party OAuth sync is planned for future cloud integrations. |
| *Public Calendar Links* | ⏳ *Roadmap* | Enforces authenticated role-based sharing (`VIEW` / `EDIT`) with anti-enumeration protection to preserve strict privacy guarantees. |
| *Agenda View* | ⏳ *Roadmap* | Interactive Month, Week, and Day views are fully supported; a dedicated linear agenda list view is queued. |
| *Offline / PWA Support* | ⏳ *Roadmap* | Service Worker offline caching is planned for upcoming releases. |

### Feature Deep-Dive

#### 1. Drag-and-Drop Scheduling & Duration Resizing
* Built on FullCalendar's interaction plugin (`handleEventDrop` and `handleEventResize` in [`CalendarView.jsx`](file:///Users/rishavsair/Documents/rishavproj/Zoner/frontend/src/components/calendar/CalendarView.jsx#L138-L203)).
* Drag any non-recurring event in Week or Day view to reschedule it instantly.
* Grab the top or bottom border of an event to resize its start or end duration.
* If moving a recurring event or encountering a backend collision, the change reverts automatically (`dropInfo.revert()`) and prompts the user with actionable feedback.

#### 2. Conflict Visualization & Live Availability
* Powered by `AvailabilityService` on the backend and a debounced watcher in [`EventModal.jsx`](file:///Users/rishavsair/Documents/rishavproj/Zoner/frontend/src/components/events/EventModal.jsx#L190-L222).
* As users modify start/end times or event dates, Zoner tests mathematical interval intersections ($start_A < end_B \land end_A > start_B$) against all visible calendars.
* Displays a real-time amber warning banner detailing overlapping meetings before changes are committed.

#### 3. Attendees & RSVP Management
* Relational schema backed by Flyway migrations (`event_attendees` table with composite unique constraint `UNIQUE (event_id, email)`).
* Supports invitee email entry, duplicate prevention, and distinct RSVP statuses (`PENDING`, `ACCEPTED`, `TENTATIVE`, `DECLINED`).
* Visual indicators and color-coded status badges in both [`EventModal.jsx`](file:///Users/rishavsair/Documents/rishavproj/Zoner/frontend/src/components/events/EventModal.jsx#L532-L619) and [`EventDetailModal.jsx`](file:///Users/rishavsair/Documents/rishavproj/Zoner/frontend/src/components/events/EventDetailModal.jsx#L113-L144).

#### 4. Dark Mode Support
* Integrated `ThemeContext` ([`ThemeContext.jsx`](file:///Users/rishavsair/Documents/rishavproj/Zoner/frontend/src/context/ThemeContext.jsx)) automatically respects user system preferences (`prefers-color-scheme: dark`) and persists overrides in `localStorage`.
* Handcrafted dark color palette utilizing Tailwind CSS `dark:` utilities across all views, modal backdrops, dropdowns, and form inputs.

#### 5. Keyboard Shortcuts
* Global keyboard listener ([`CalendarPage.jsx`](file:///Users/rishavsair/Documents/rishavproj/Zoner/frontend/src/pages/CalendarPage.jsx#L65-L75)) captures `⌘K` (macOS) and `Ctrl+K` (Windows/Linux).
* Instantly launches the debounced global search palette ([`SearchModal.jsx`](file:///Users/rishavsair/Documents/rishavproj/Zoner/frontend/src/components/search/SearchModal.jsx)) with keyboard navigation.

#### 6. Optimistic UI Updates
* Event drag-and-drop and resize actions render immediately on the UI canvas while issuing background asynchronous `PUT /api/events/:id` requests.
* Calendar visibility checkboxes in the sidebar toggle visible layers instantaneously with optimistic local state while persisting preferences asynchronously via `PUT /api/calendars/:id/preferences`.

#### 7. Audit History & Activity Logs
* **Reminder Dispatch Locks:** The `reminder_dispatches` table acts as a tamper-proof system audit log recording every successful alert delivery timestamp.
* **Token Security Audits:** Personal Access Tokens log their `last_used_at` timestamp on every MCP request.
* **Structured Observability:** Centralized logging across authentication, event lifecycle, and recurrence exception resolutions with MDC correlation IDs.

#### 8. Conversational AI Integration (MCP Tools)
* Exposes dedicated Model Context Protocol tools (`create_event`, `list_events`, `check_availability`, `search_events`).
* External AI assistants (Claude, Cursor, Claude Code) interpret natural language prompts (e.g., *"Schedule team sync tomorrow at 3pm"*, *"Summarize my meetings for this week"*, *"Find a 45-minute open slot on Thursday"*) and invoke Zoner's tools directly.

#### 9. Pluggable Email Notification Architecture
* Backed by `EmailNotificationChannel` implemented alongside `InAppNotificationChannel` within `NotificationChannelRegistry`.
* Provides structured logging of email dispatches and automatic in-app inbox fallback persistence so reminders are never lost.

---

## 6. Model Context Protocol (MCP) Integration

Zoner exposes a fully compliant **Model Context Protocol (MCP)** server (spec `2024-11-05`), turning AI assistants into intelligent calendar agents.

### Supported Tools:
1. `list_calendars`: Returns all personal and shared calendars with ownership and permissions.
2. `create_calendar`: Creates a new calendar with a custom name and color.
3. `list_events`: Queries single and recurring events expanded across a date range.
4. `get_event`: Retrieves full details, reminders, and exceptions for a specific event.
5. `create_event`: Schedules single or recurring events with conflict checking.
6. `update_event`: Reschedules or edits existing events.
7. `delete_event`: Cancels events or recurring series.
8. `search_events`: Keyword search across accessible calendars.
9. `check_availability`: Queries time ranges for existing meetings and conflicts.

### Connecting Claude Desktop:
Edit `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) or `%APPDATA%\Claude\claude_desktop_config.json` (Windows):
```json
{
  "mcpServers": {
    "zoner": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "https://zoner-backend.onrender.com/api/mcp",
        "--header",
        "Authorization: Bearer zoner_pat_YOUR_TOKEN_HERE"
      ]
    }
  }
}
```

### Connecting Cursor IDE:
In Cursor Settings $\rightarrow$ **Features** $\rightarrow$ **MCP** $\rightarrow$ **Add New MCP Server**:
* **Name:** `Zoner Calendar`
* **Type:** `command`
* **Command:** `npx -y mcp-remote https://zoner-backend.onrender.com/api/mcp --header "Authorization: Bearer zoner_pat_YOUR_TOKEN_HERE"`

*(Generate your personal token inside Zoner via **User Menu → Settings & MCP**)*.

---

## 7. Local Development Setup

### Prerequisites
* **Java Development Kit (JDK):** Version 21 LTS
* **Node.js:** Version 20+ LTS
* **PostgreSQL:** Neon account or local PostgreSQL 16
* **Git**

### Step 1: Clone Repository
```bash
git clone https://github.com/rishavsair/Zoner.git
cd Zoner
```

### Step 2: Configure Environment
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
Provide your database connection details:
```ini
DB_URL=jdbc:postgresql://<HOST>:<PORT>/<DATABASE>?sslmode=require
DB_USER=<USER>
DB_PASSWORD=<PASSWORD>
DB_POOL_SIZE=5
PORT=8080
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
JWT_SECRET=zoner-super-secure-default-jwt-secret-key-that-is-at-least-256-bits-long-for-hmac-sha-256
```

### Step 3: Run Backend
```bash
cd backend
./mvnw spring-boot:run
```
Flyway automatically applies all migrations (`V1` to `V7`) and `DataSeeder` provisions the demo accounts.
* Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* Health Check: [http://localhost:8080/healthz](http://localhost:8080/healthz)

### Step 4: Run Frontend
In a new terminal:
```bash
cd frontend
npm install
npm run dev
```
Open [http://localhost:5173](http://localhost:5173) in your browser.

### Step 5: Run Automated Tests
```bash
# Backend unit & integration tests
cd backend
./mvnw test

# Frontend production build
cd ../frontend
npm run build
```

---

## 8. Configuration & Environment Variables

| Variable | Target | Purpose | Example |
|---|---|---|---|
| `DB_URL` | Backend | PostgreSQL JDBC URL with SSL | `jdbc:postgresql://ep-...neon.tech/neondb?sslmode=require` |
| `DB_USER` | Backend | Database username | `neondb_owner` |
| `DB_PASSWORD` | Backend | Database password | `secret_password` |
| `DB_POOL_SIZE` | Backend | HikariCP maximum connection pool size | `5` (tuned for free tier) |
| `PORT` | Backend | HTTP server listening port | `8080` (or `$PORT` on Render) |
| `CORS_ALLOWED_ORIGINS`| Backend | Comma-separated or regex allowed origins | `http://localhost:5173,https://.*\.onrender\.com` |
| `JWT_SECRET` | Backend | HMAC-SHA256 signing secret key (min 256 bits)| `zoner-super-secure-default-jwt-secret-...` |
| `VITE_API_URL` | Frontend | Backend API base URL | `https://zoner-backend.onrender.com` |

---

## 9. Documentation Index

Detailed architectural and procedural documents are maintained in the repository:

* **[User & Reviewer Guide (`docs/HOW_TO_USE.md`)](docs/HOW_TO_USE.md)**: Step-by-step evaluator walkthrough covering UI navigation, calendar sharing, conflict detection, and MCP tool testing.
* **[Architecture & Decision Log (`docs/DECISIONS.md`)](docs/DECISIONS.md)**: Exhaustive rationale for all 10 architectural decisions taken from scratch to end.
* **[MCP Authentication & Protocol Spec (`docs/MCP_AUTH.md`)](docs/MCP_AUTH.md)**: Security model, SHA-256 PAT hashing, and JSON-RPC 2.0 tool transport specification.
* **[AI Usage Log (`AI_USAGE.md`)](AI_USAGE.md)**: Running log of AI prompts, code generation, human reviews, line-by-line verification, and bug refactorings.
* **[Architecture Decision Records (`docs/adr/`)](docs/adr/)**:
  * [ADR-001: Technology Stack](docs/adr/001-stack.md)
  * [ADR-002: Time Model & Time Zones](docs/adr/002-time-model.md)
  * [ADR-003: Recurrence Engine & Exceptions](docs/adr/003-recurrence.md)
  * [ADR-004: Centralized AccessPolicy & Anti-Enumeration](docs/adr/004-access-policy.md)
  * [ADR-005: Model Context Protocol & PAT Auth](docs/adr/005-mcp-pat.md)
  * [ADR-006: Reminder Dispatcher Idempotency](docs/adr/006-reminders-idempotency.md)
  * [ADR-007: Zero-Cost Cloud Deployment on Render](docs/adr/007-deployment-render.md)

---

## 10. Known Limitations & Production Scope

In the spirit of honest senior engineering, the following intentional trade-offs are documented:

1. **Free-Tier Cold Starts:** On Render's free tier, the backend web service spins down after 15 minutes of inactivity. First requests after sleep incur a 30–50 second wake-up delay.
2. **Email Delivery Channel:** Outbound SMTP port 587/465 is blocked by Render on free-tier services to prevent spam. Reminders are fully dispatched via the in-app notification center and REST API; the email channel exists as an extensible stub.
3. **Optimistic Concurrency:** In high-concurrency environments where multiple collaborators edit the same event simultaneously, optimistic locking (`@Version`) rejects stale updates with `409 Conflict`.
