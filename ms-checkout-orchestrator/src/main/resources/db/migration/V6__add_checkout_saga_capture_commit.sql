ALTER TABLE checkout_saga
    ADD COLUMN IF NOT EXISTS payment_captured BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS inventory_committed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS attempts_payment_capture INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS attempts_inventory_commit INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS payment_capture_command_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS inventory_commit_command_id VARCHAR(64);
