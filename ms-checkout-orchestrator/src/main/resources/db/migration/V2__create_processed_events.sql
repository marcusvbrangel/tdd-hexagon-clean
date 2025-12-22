CREATE TABLE IF NOT EXISTS processed_events (
    event_id        VARCHAR(128) PRIMARY KEY,
    event_type      VARCHAR(128) NOT NULL,
    order_id        VARCHAR(64)  NOT NULL,
    processed_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_processed_events_order
ON processed_events (order_id);

CREATE INDEX IF NOT EXISTS idx_processed_events_type_time
ON processed_events (event_type, processed_at);
