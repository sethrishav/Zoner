-- Event exceptions table (single-occurrence edits and cancellations for recurring event series)
CREATE TABLE event_exceptions (
    id                 BIGSERIAL PRIMARY KEY,
    event_id           BIGINT       NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    original_start     TIMESTAMPTZ  NOT NULL,
    exception_type     VARCHAR(20)  NOT NULL, -- 'CANCELLED' or 'MODIFIED'
    override_title     VARCHAR(255),
    override_desc      TEXT,
    override_location  VARCHAR(255),
    override_color     VARCHAR(30),
    override_start_at  TIMESTAMPTZ,
    override_end_at    TIMESTAMPTZ,
    override_all_day   BOOLEAN,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_event_exceptions_event_orig_start UNIQUE (event_id, original_start)
);

CREATE INDEX idx_event_exceptions_event_id ON event_exceptions (event_id);
