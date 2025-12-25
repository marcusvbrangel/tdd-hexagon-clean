CREATE TABLE IF NOT EXISTS inventory_items (
    product_id      VARCHAR(64) PRIMARY KEY,
    on_hand         BIGINT NOT NULL CHECK (on_hand >= 0),
    reserved        BIGINT NOT NULL CHECK (reserved >= 0),
    updated_at      TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS inventory_reservations (
    reservation_id   VARCHAR(64) PRIMARY KEY,
    order_id         VARCHAR(64) NOT NULL UNIQUE,
    status           VARCHAR(16) NOT NULL,
    reason           VARCHAR(128),
    created_at       TIMESTAMPTZ NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,
    last_command_id  VARCHAR(64),
    correlation_id   VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_inv_res_status_expires
ON inventory_reservations (status, expires_at);

CREATE TABLE IF NOT EXISTS inventory_reservation_items (
    id              BIGSERIAL PRIMARY KEY,
    reservation_id  VARCHAR(64) NOT NULL REFERENCES inventory_reservations (reservation_id) ON DELETE CASCADE,
    product_id      VARCHAR(64) NOT NULL,
    quantity        BIGINT NOT NULL CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_inv_res_item_reservation
ON inventory_reservation_items (reservation_id);

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
