-- Event Attendees and RSVP table
CREATE TABLE event_attendees (
    id           BIGSERIAL PRIMARY KEY,
    event_id     BIGINT       NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    email        VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    status       VARCHAR(30)  NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'ACCEPTED', 'DECLINED', 'TENTATIVE'
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_event_attendee UNIQUE (event_id, email)
);

CREATE INDEX idx_event_attendees_event_id ON event_attendees (event_id);
CREATE INDEX idx_event_attendees_email ON event_attendees (email);
