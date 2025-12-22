CREATE TABLE IF NOT EXISTS checkout_saga (
    order_id            VARCHAR(64) PRIMARY KEY,
    saga_id             VARCHAR(64)  NOT NULL,
    correlation_id      VARCHAR(64)  NOT NULL,

    status              VARCHAR(32)  NOT NULL,
    step                VARCHAR(64)  NOT NULL,

    customer_id         VARCHAR(64),
    amount              VARCHAR(32),
    currency            VARCHAR(8),

    order_completed     BOOLEAN NOT NULL DEFAULT FALSE,
    inventory_released  BOOLEAN NOT NULL DEFAULT FALSE,
    order_canceled      BOOLEAN NOT NULL DEFAULT FALSE,

    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    version             BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_checkout_saga_saga_id
ON checkout_saga (saga_id);

CREATE INDEX IF NOT EXISTS idx_checkout_saga_status_step
ON checkout_saga (status, step);

CREATE INDEX IF NOT EXISTS idx_checkout_saga_updated_at
ON checkout_saga (updated_at);
