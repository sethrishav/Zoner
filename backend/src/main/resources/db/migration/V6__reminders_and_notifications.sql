-- Reminder dispatches table (ensures idempotent delivery across instances)
CREATE TABLE reminder_dispatches (
    id               BIGSERIAL PRIMARY KEY,
    reminder_id      BIGINT      NOT NULL REFERENCES reminders(id) ON DELETE CASCADE,
    occurrence_start TIMESTAMPTZ NOT NULL,
    fire_at          TIMESTAMPTZ NOT NULL,
    status           VARCHAR(20) NOT NULL, -- 'SENT', 'FAILED'
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_reminder_dispatches_reminder_occ UNIQUE (reminder_id, occurrence_start)
);

CREATE INDEX idx_reminder_dispatches_reminder_id ON reminder_dispatches (reminder_id);
CREATE INDEX idx_reminder_dispatches_fire_at ON reminder_dispatches (fire_at);

-- In-app notifications table
CREATE TABLE notifications (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id         BIGINT       REFERENCES events(id) ON DELETE SET NULL,
    occurrence_start TIMESTAMPTZ  NOT NULL,
    title            VARCHAR(255) NOT NULL,
    message          TEXT         NOT NULL,
    read_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_id ON notifications (user_id);
CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);
