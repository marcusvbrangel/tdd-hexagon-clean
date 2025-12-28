CREATE TABLE IF NOT EXISTS payments (
    payment_id      VARCHAR(64) PRIMARY KEY,
    order_id        VARCHAR(64) NOT NULL UNIQUE,
    customer_id     VARCHAR(64),
    status          VARCHAR(16) NOT NULL,
    amount          NUMERIC(19, 2) NOT NULL,
    currency        VARCHAR(8) NOT NULL,
    payment_method  VARCHAR(64),
    reason          VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    last_command_id VARCHAR(64),
    correlation_id  VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_payments_status
ON payments (status);

CREATE TABLE IF NOT EXISTS processed_messages (
    message_id     VARCHAR(64) PRIMARY KEY,
    message_type   VARCHAR(64) NOT NULL,
    aggregate_id   VARCHAR(64) NOT NULL,
    processed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_processed_messages_aggregate
ON processed_messages (aggregate_id);

CREATE INDEX IF NOT EXISTS idx_processed_messages_type_time
ON processed_messages (message_type, processed_at);
