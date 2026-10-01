-- Baseline migration: proves Flyway runs on every environment.
-- Real tables arrive with their milestones (users in M1, calendars in M2, events in M3, ...).
CREATE TABLE app_metadata (
    key        TEXT PRIMARY KEY,
    value      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO app_metadata (key, value) VALUES ('schema_baseline', 'M0');
