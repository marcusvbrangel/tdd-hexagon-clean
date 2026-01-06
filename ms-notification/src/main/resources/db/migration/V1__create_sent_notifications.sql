CREATE TABLE IF NOT EXISTS sent_notifications (
    command_id   VARCHAR(64) PRIMARY KEY,
    template     VARCHAR(64) NOT NULL,
    order_id     VARCHAR(64) NOT NULL,
    customer_id  VARCHAR(64),
    recipient    VARCHAR(256),
    status       VARCHAR(16) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at      TIMESTAMPTZ,
    error        TEXT
);

CREATE INDEX IF NOT EXISTS idx_sent_notifications_order
ON sent_notifications (order_id);

CREATE INDEX IF NOT EXISTS idx_sent_notifications_status
ON sent_notifications (status);
