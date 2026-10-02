-- Calendars table
CREATE TABLE calendars (
    id          BIGSERIAL PRIMARY KEY,
    owner_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    color       VARCHAR(30)  NOT NULL DEFAULT '#3B82F6',
    is_default  BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_calendars_owner_id ON calendars (owner_id);

-- Calendar shares table (sharing with VIEW or EDIT permissions)
CREATE TABLE calendar_shares (
    id          BIGSERIAL PRIMARY KEY,
    calendar_id BIGINT      NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission  VARCHAR(20) NOT NULL, -- 'VIEW' or 'EDIT'
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_calendar_shares_calendar_user UNIQUE (calendar_id, user_id)
);

CREATE INDEX idx_calendar_shares_user_id ON calendar_shares (user_id);
CREATE INDEX idx_calendar_shares_calendar_id ON calendar_shares (calendar_id);

-- User calendar preferences (per-user visibility and optional color override)
CREATE TABLE user_calendar_prefs (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    calendar_id    BIGINT      NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
    enabled        BOOLEAN     NOT NULL DEFAULT true,
    color_override VARCHAR(30),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_calendar_prefs_user_calendar UNIQUE (user_id, calendar_id)
);

CREATE INDEX idx_user_calendar_prefs_user ON user_calendar_prefs (user_id);

