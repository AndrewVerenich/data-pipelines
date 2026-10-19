CREATE TABLE IF NOT EXISTS membership_snapshot (
    user_id     TEXT NOT NULL,
    audience    TEXT NOT NULL,
    email       TEXT NOT NULL,
    cart_value  DOUBLE PRECISION NOT NULL DEFAULT 0,
    version     TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'active',
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, audience)
);

CREATE INDEX IF NOT EXISTS idx_membership_active
    ON membership_snapshot (audience, status);

CREATE TABLE IF NOT EXISTS delivery_log (
    id               BIGSERIAL PRIMARY KEY,
    run_id           BIGINT,
    user_id          TEXT NOT NULL,
    audience         TEXT NOT NULL,
    action           TEXT NOT NULL,
    version          TEXT NOT NULL,
    idempotency_key  TEXT NOT NULL UNIQUE,
    status           TEXT NOT NULL,
    http_status      INT,
    detail           TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dead_letter (
    id               BIGSERIAL PRIMARY KEY,
    user_id          TEXT NOT NULL,
    audience         TEXT NOT NULL,
    action           TEXT NOT NULL,
    version          TEXT NOT NULL,
    idempotency_key  TEXT NOT NULL UNIQUE,
    http_status      INT,
    error            TEXT,
    payload          JSONB,
    resolved         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sync_runs (
    id            BIGSERIAL PRIMARY KEY,
    started_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at   TIMESTAMPTZ,
    status        TEXT NOT NULL DEFAULT 'running',
    enters        INT NOT NULL DEFAULT 0,
    leaves        INT NOT NULL DEFAULT 0,
    updates       INT NOT NULL DEFAULT 0,
    delivered     INT NOT NULL DEFAULT 0,
    dead_letters  INT NOT NULL DEFAULT 0,
    lag_sec       BIGINT NOT NULL DEFAULT 0,
    error         TEXT
);

CREATE TABLE IF NOT EXISTS email_api_calls (
    id               BIGSERIAL PRIMARY KEY,
    user_id          TEXT NOT NULL,
    audience         TEXT NOT NULL,
    action           TEXT NOT NULL,
    version          TEXT NOT NULL,
    idempotency_key  TEXT NOT NULL,
    http_status      INT NOT NULL,
    outcome          TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_email_api_calls_created
    ON email_api_calls (created_at DESC);
