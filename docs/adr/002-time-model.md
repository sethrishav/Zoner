# ADR-002: Time model (instant vs local date-time vs time zone)

- **Status:** Accepted (M0)

## Vocabulary
- **Instant**: one exact point on the global timeline. Stored as `timestamptz` (UTC).
- **Local date-time**: wall-clock time with no zone ("Monday 10:00"). It names no single moment on its own.
- **Time zone**: an IANA id (for example `Asia/Kolkata`) with rules that turn a local date-time into an instant on a given date, including DST.

## Decision
1. **One-off events** store `start_at` / `end_at` as instants, plus the event's `time_zone` for display and editing.
2. **Recurring events** store the first occurrence as a local date-time plus `time_zone`, and expand each occurrence in that zone. "Standup every Monday 10:00" therefore stays at 10:00 local time across DST changes, even though its UTC instant shifts.
3. **All-day events** are dates, not instants, so they never shift when the viewer's zone changes.
4. The server runs in UTC (`hibernate.jdbc.time_zone=UTC`, Jackson `time-zone: UTC`). All API timestamps are ISO-8601 with an offset or `Z`.
5. Each user has a profile time zone, used as the default for display and as the default for MCP tools that omit `timezone`.
6. Code never calls `Instant.now()` directly. It injects the `Clock` bean so time-dependent logic is testable.

## Consequences
- Recurrence expansion needs the zone rules, so it is done in Java (`java.time.ZoneId`), never in SQL.
- Tests must cover DST transitions (spring forward and fall back), cross-zone creation and all-day events.
