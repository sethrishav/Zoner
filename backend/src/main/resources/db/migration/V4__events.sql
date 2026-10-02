-- Enable pg_trgm extension for fuzzy and substring search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Events table
CREATE TABLE events (
    id               BIGSERIAL PRIMARY KEY,
    calendar_id      BIGINT       NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    location         VARCHAR(255),
    color            VARCHAR(30),
    all_day          BOOLEAN      NOT NULL DEFAULT false,
    start_at         TIMESTAMPTZ  NOT NULL,
    end_at           TIMESTAMPTZ  NOT NULL,
    start_local      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    end_local        TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    time_zone        VARCHAR(100) NOT NULL,
    recurrence_rule  TEXT,
    recurrence_until TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_by       BIGINT       NOT NULL REFERENCES users(id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_calendar_id ON events (calendar_id);
CREATE INDEX idx_events_calendar_range ON events (calendar_id, start_at, end_at);
CREATE INDEX idx_events_created_by ON events (created_by);
CREATE INDEX idx_events_search_title ON events USING gin (title gin_trgm_ops);
CREATE INDEX idx_events_search_desc ON events USING gin (description gin_trgm_ops);
CREATE INDEX idx_events_search_loc ON events USING gin (location gin_trgm_ops);

-- Reminders table
CREATE TABLE reminders (
    id             BIGSERIAL PRIMARY KEY,
    event_id       BIGINT      NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    minutes_before INT         NOT NULL,
    channel        VARCHAR(20) NOT NULL, -- 'IN_APP', 'EMAIL', 'SMS'
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reminders_event_id ON reminders (event_id);
