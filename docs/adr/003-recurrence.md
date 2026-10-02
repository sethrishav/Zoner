# ADR-003: Recurrence Engine (RFC 5545 RRULE & Exception Model)

- **Status:** Accepted (M4)

## Context
Calendar applications must support recurring events (daily, weekly, monthly, custom days). The core architectural question is whether to **materialize** all future occurrences as individual rows in PostgreSQL or **expand at query time** from a master recurrence rule. Furthermore, users require the ability to modify or cancel individual occurrences without corrupting the historical record or duplicating series definitions.

## Decision
1. **Query-Time Expansion via RFC 5545 `RRULE`:**
   - Recurring events store a single master row in `events` with an iCalendar RFC 5545 `rrule` string (e.g., `FREQ=WEEKLY;BYDAY=MO,WE,FR`).
   - The backend uses `lib-recur` to dynamically compute occurrence timestamps during event range queries (`[timeMin, timeMax]`).
   - Materializing infinite future rows is rejected.
2. **Three-Tier Modification Model:**
   - **`THIS` (Single Occurrence):** Inserts an `event_exceptions` row with `original_start_at` referencing the target occurrence. If cancelled: `is_cancelled = true`. If rescheduled/modified: custom `start_at`, `end_at`, `title`, or `location` overrides are stored.
   - **`THIS_AND_FOLLOWING` (Series Split):** Truncates the existing master event's `RRULE` with an `UNTIL` clause set to the day before the split, and creates a new master recurring event starting from the split date.
   - **`ALL` (Master Update):** Directly updates the master `events` record.

## Consequences
- Database storage is $O(1)$ per recurring series regardless of duration.
- Historical occurrences remain immutable when future occurrences are rescheduled.
- Range queries must always supply `timeMin` and `timeMax` bounds to constrain expansion.
