ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS provider_payment_id VARCHAR(64);

UPDATE payments
SET provider_payment_id = payment_id
WHERE provider_payment_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_provider_payment_id
ON payments (provider_payment_id);
