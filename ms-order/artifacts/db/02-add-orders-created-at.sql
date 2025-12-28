DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'orders'
          AND column_name = 'placed_at'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'orders'
          AND column_name = 'created_at'
    ) THEN
        EXECUTE 'ALTER TABLE orders RENAME COLUMN placed_at TO created_at';
    END IF;
END $$;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'orders'
          AND column_name = 'placed_at'
    ) THEN
        EXECUTE 'UPDATE orders SET created_at = COALESCE(created_at, placed_at) WHERE created_at IS NULL';
    END IF;
END $$;

UPDATE orders
SET created_at = NOW()
WHERE created_at IS NULL
  AND status <> 'DRAFT';
