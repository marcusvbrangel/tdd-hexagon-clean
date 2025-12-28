ALTER TABLE checkout_saga
    ADD COLUMN IF NOT EXISTS inventory_reserve_command_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS payment_authorize_command_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS order_complete_command_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS inventory_release_command_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS order_cancel_command_id VARCHAR(64);
