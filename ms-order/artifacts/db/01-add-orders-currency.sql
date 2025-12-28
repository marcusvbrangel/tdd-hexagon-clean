ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS currency VARCHAR(8);

UPDATE orders
SET currency = 'BRL'
WHERE currency IS NULL;
