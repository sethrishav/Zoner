# Zoner: User & Reviewer Guide

Welcome to **Zoner** — a full-stack, time-zone intelligent calendar platform built with Spring Boot 3.5 (Java 21), React 18, PostgreSQL 16 (Neon), and a built-in Model Context Protocol (MCP) server.

This guide provides a comprehensive walkthrough for evaluators, reviewers, and users to explore every capability of the application both via the graphical user interface and through AI agents (Claude Desktop, Cursor, Claude Code).

---

## 1. Quick-Start (Reviewer 1-Minute Path)

You can test the entire platform without local installation using our deployed production services:

| Resource | URL | Details |
|---|---|---|
| **Web Application** | [https://zoner-6uv2.onrender.com](https://zoner-6uv2.onrender.com) | Responsive React SPA hosted on Render |
| **API Backend** | [https://zoner-backend.onrender.com](https://zoner-backend.onrender.com) | Spring Boot 3.5 on Java 21 |
| **Swagger UI** | [https://zoner-backend.onrender.com/swagger-ui.html](https://zoner-backend.onrender.com/swagger-ui.html) | Interactive OpenAPI 3.0 documentation |
| **Health Check** | [https://zoner-backend.onrender.com/healthz](https://zoner-backend.onrender.com/healthz) | Production liveness & database ping |
| **MCP Server** | [https://zoner-backend.onrender.com/api/mcp](https://zoner-backend.onrender.com/api/mcp) | Streamable HTTP & SSE MCP endpoint |

### Pre-Seeded Demo Accounts
The database is pre-seeded with sample calendars, events, and recurrence rules:

* **Primary Account (Reviewer):**
  * **Email:** `demo@zoner.app`
  * **Password:** `Password123!`
  * *(Tip: Click the "Demo Reviewer" quick-fill button on the login screen to fill credentials instantly)*
* **Collaborator Account:**
  * **Email:** `colleague@zoner.app`
  * **Password:** `Password123!`
  * *(Owns shared calendars "Project Alpha" and "Company Announcements")*

> [!NOTE]
> **Free-Tier Sleep Notice:** Render free-tier web services sleep after 15 minutes of inactivity. If the site is waking up from a cold start, the initial page load or API call may take 30–50 seconds. Once awake, performance is snappy.

---

## 2. Navigating the Calendar Interface

### View Switching & Navigation
* **Calendar Views:** Switch between **Month**, **Week**, and **Day** views using the toggle buttons in the top-right header.
* **Date Navigation:** Use the **Previous (<)**, **Next (>)**, and **Today** buttons in the header, or click any date on the **Mini-Month Navigator** in the left sidebar to jump directly to that date.
* **Responsive Layout:** On mobile or narrow viewports, click the hamburger menu icon in the top navbar to toggle the calendar sidebar.

### Managing Multiple Calendars
The left sidebar displays all your calendars divided into personal and shared calendars:
1. **Toggle Visibility:** Click the checkbox next to any calendar name to instantly toggle its events on or off in the main calendar grid.
2. **Create New Calendar:**
   * Click the **`+`** icon next to "My Calendars" in the sidebar.
   * Provide a name (e.g. *"Client Calls"*), an optional description, and select a brand accent color.
   * The calendar is immediately created and selected.
3. **Default Calendar Protection:** Every user is automatically provisioned a primary "Personal" calendar on signup. This calendar cannot be deleted, ensuring scheduling integrity.

---

## 3. Sharing & Collaboration (Access Matrix)

Zoner provides granular role-based sharing controls:

### Sharing a Calendar
1. In the sidebar, locate a calendar you own (e.g., "Work").
2. Click the **Share (User+)** icon next to the calendar name.
3. Enter the email address of the person you wish to share with (e.g., `colleague@zoner.app`).
4. Select the permission level:
   * **`VIEW` (Read Only):** The user can see event titles, times, and descriptions, but cannot modify or delete events.
   * **`EDIT` (Collaborative):** The user has full permission to create, update, reschedule, and delete events on this calendar.
5. Click **"Share Calendar"**.

### Verifying Permissions & Anti-Enumeration
* **Pre-seeded Shared Calendars:**
  * Log in as `demo@zoner.app`: you will see **"Project Alpha"** (shared with `EDIT` access) and **"Company Announcements"** (shared with `VIEW` access).
  * Try creating or editing an event on **"Project Alpha"**: changes succeed immediately.
  * Try modifying or deleting an event on **"Company Announcements"**: the UI disables editing controls, and any direct API call returns `403 Forbidden`.
* **Anti-Enumeration Security:** Querying an event or calendar belonging to a user who has not shared it with you returns `404 Not Found` (never `403`), preventing malicious actors from scanning ID spaces to discover other users' calendar IDs.

---

## 4. Scheduling & Managing Events

### Creating an Event
1. **Quick-Click or Drag:** Click on any empty date/time slot in the grid, or click and drag across a time range (e.g. 2:00 PM to 3:30 PM).
2. The **Event Modal** opens automatically with the selected time range pre-filled:
   * **Title:** Event summary (e.g. *"Engineering Standup"*).
   * **Calendar:** Target calendar from your accessible calendars.
   * **Color:** Custom accent color (defaults to calendar theme).
   * **Date & Time:** Precise start and end times, or toggle **"All-day event"**.
   * **Location:** Meeting room, physical address, or video link (Google Meet / Zoom).
   * **Description:** Agenda, meeting notes, or links.

### Intelligent Conflict & Availability Checking
* When you pick a time slot in the Event Modal, Zoner automatically checks all your visible calendars for overlapping meetings in real-time.
* If a conflict is detected, an amber **"Scheduling Conflict Detected"** warning alert appears inline before you save, showing the exact overlapping event title and time range.

### Setting Reminders
* In the Event Modal, select an in-app reminder interval (e.g., *10 minutes before*, *15 minutes before*, or *1 hour before*).
* When the event approaches, Zoner's background dispatcher delivers a notification to your in-app inbox.

---

## 5. Recurring Events (RFC 5545 RRULE)

Zoner supports full iCalendar RFC 5545 recurrence rules with DST-safe local wall-clock preservation.

### Creating a Recurring Series
1. Open the Event Modal.
2. In the **"Repeats"** dropdown, select an interval:
   * **Daily** (`FREQ=DAILY`)
   * **Weekly** (`FREQ=WEEKLY;BYDAY=MO,WE,FR` or customized weekdays)
   * **Monthly** (`FREQ=MONTHLY`)
3. Save the event. The occurrences expand dynamically across your calendar view without creating thousands of duplicate database rows.

### Editing or Deleting Recurring Occurrences
When you click on any occurrence of a recurring series and choose to edit or delete it, Zoner presents the standard calendar industry modal with three choices:

```
┌────────────────────────────────────────────────────────┐
│              Edit Recurring Event                      │
├────────────────────────────────────────────────────────┤
│  ○ This event only                                     │
│    Modifies only this specific date without affecting  │
│    future or past meetings. (Creates an exception)     │
│                                                        │
│  ○ This and all following events                       │
│    Splits the series into two: preserves past history  │
│    and starts a new series from this date onward.      │
│                                                        │
│  ○ All events in the series                            │
│    Updates the master series definition across all     │
│    occurrences.                                        │
└────────────────────────────────────────────────────────┘
```

* **DST Wall-Clock Safety:** If you schedule a recurring standup at 10:00 AM America/New_York, it will remain at 10:00 AM wall-clock time even when Daylight Saving Time transitions between EST and EDT.

---

## 6. Global Search (`⌘K` / `Ctrl+K`)

1. Press **`⌘K`** (macOS) or **`Ctrl+K`** (Windows/Linux) anywhere in the application, or click the **"Search events..."** button in the top navigation bar.
2. Type any query (e.g., *"Sprint"*, *"Alpha"*, *"Standup"*, or *"Review"*).
3. Search queries are debounced (300 ms) and match event titles, descriptions, and locations across all accessible calendars.
4. Click any search result to open its full details modal and jump directly to that date on the calendar.

---

## 7. In-App Notifications & Reminders

1. In the top navigation bar, click the **Bell Icon**.
2. A badge shows the number of unread notifications.
3. The dropdown displays recently dispatched reminders with event titles, occurrence start times, and delivery timestamps.
4. Click **"Mark as read"** or **"Mark all as read"** to clear unread badges.

---

## 8. Model Context Protocol (MCP) Integration

Zoner includes a fully compliant **Model Context Protocol (MCP)** server (`2024-11-05` specification), allowing AI assistants to act as intelligent scheduling agents.

### Step 1: Generate a Personal Access Token (PAT)
1. Log in to Zoner.
2. In the top-right corner, click your profile avatar and select **"Settings & MCP"**.
3. In the modal, enter a token name (e.g., `"Cursor IDE"` or `"Claude Desktop"`).
4. Select an expiration period (default: 30 days) and click **"Generate Token"**.
5. Copy the generated token (`zoner_pat_...`). *(For security, this token is shown only once and hashed with SHA-256 in the database).*

---

### Step 2: Connect Claude Desktop
Open your Claude Desktop configuration file:
* **macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
* **Windows:** `%APPDATA%\Claude\claude_desktop_config.json`

Add the Zoner MCP server entry:
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
Restart Claude Desktop. The hammer icon in Claude will light up with Zoner's 9 calendar tools.

---

### Step 3: Connect Cursor IDE
1. Open Cursor Settings $\rightarrow$ **Features** $\rightarrow$ **MCP**.
2. Click **"+ Add New MCP Server"**.
3. Enter the following:
   * **Name:** `Zoner Calendar`
   * **Type:** `command`
   * **Command:** `npx -y mcp-remote https://zoner-backend.onrender.com/api/mcp --header "Authorization: Bearer zoner_pat_YOUR_TOKEN_HERE"`
4. Save. Cursor will connect and display the green active status light.

---

### Step 4: Available MCP Tools & Sample Prompts

| Tool Name | Description |
|---|---|
| `list_calendars` | Lists all personal and shared calendars with permissions |
| `create_calendar` | Creates a new calendar with custom color |
| `list_events` | Retrieves all events within a date range |
| `get_event` | Fetches details and reminders for a specific event |
| `create_event` | Schedules single or recurring events with conflict checking |
| `update_event` | Reschedules or updates existing events |
| `delete_event` | Cancels single events or recurring series |
| `search_events` | Full-text search across accessible calendars |
| `check_availability` | Checks time ranges for scheduling conflicts |

#### Sample Natural Language Prompts to Test with your AI:
* *"What meetings do I have scheduled for the next 3 days?"*
* *"Check if I am available for a 45-minute sync on Thursday at 2:00 PM."*
* *"Schedule an Architecture Review on my Work calendar next Monday from 10:00 AM to 11:30 AM with a Google Meet link."*
* *"Search my calendar for any events mentioning 'Milestone' or 'Launch'."*
* *"Create a recurring team standup every Monday and Wednesday at 9:30 AM America/New_York."*
