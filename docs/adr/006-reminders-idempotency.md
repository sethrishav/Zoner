# ADR-006: Reminder Dispatch Engine & Idempotency Guarantee

- **Status:** Accepted (M5)

## Context
Scheduled reminders must be dispatched reliably when their trigger time arrives (`fire_at <= now`). In multi-instance deployments, during rapid scheduler invocations, or when restarting an application, dispatch workers risk firing duplicate notifications or losing events that fell within a downtime period.

## Decision
1. **Database-Enforced Idempotency:**
   - The `reminder_dispatches` table enforces a composite unique constraint: `UNIQUE (reminder_id, occurrence_start)`.
   - Dispatch attempts check for pre-existing records and handle constraint violations gracefully.
   - Duplicate notifications are physically impossible at the database level.
2. **Cold-Start Catch-Up Safety:**
   - The scheduled dispatcher query uses a rolling lookback window (`fire_at <= now AND fire_at >= now - INTERVAL '2 hours'`).
   - If the application was asleep on Render's free tier when a reminder was scheduled to fire, it catches up and delivers the notification immediately upon waking up.
3. **In-App Notification Channel:**
   - Notifications are stored in the `notifications` table and served via REST API (`/api/notifications`) with unread badge counters.

## Consequences
- Reliable reminder delivery regardless of server sleep cycles.
- Zero duplicate notifications across concurrent scheduler executions.
