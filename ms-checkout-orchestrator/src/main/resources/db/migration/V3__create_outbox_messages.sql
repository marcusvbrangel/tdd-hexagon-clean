CREATE TABLE IF NOT EXISTS outbox_messages (
    id              BIGSERIAL PRIMARY KEY,

    event_id         VARCHAR(128) NOT NULL,
    aggregate_type   VARCHAR(64)  NOT NULL,
    aggregate_id     VARCHAR(64)  NOT NULL,

    event_type       VARCHAR(128) NOT NULL,
    topic            VARCHAR(128) NOT NULL,

    payload_json     TEXT NOT NULL,
    headers_json     TEXT NOT NULL,

    status           VARCHAR(32) NOT NULL,
    occurred_at      TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    published_at     TIMESTAMPTZ,

    last_error       VARCHAR(512),
    retry_count      INTEGER NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ NOT NULL,

    version          BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_outbox_event_id
ON outbox_messages (event_id);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created
ON outbox_messages (status, created_at);

CREATE INDEX IF NOT EXISTS idx_outbox_status_next_attempt
ON outbox_messages (status, next_attempt_at);
