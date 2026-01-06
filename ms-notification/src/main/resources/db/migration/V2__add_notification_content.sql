ALTER TABLE sent_notifications
    ADD COLUMN IF NOT EXISTS subject VARCHAR(256);

ALTER TABLE sent_notifications
    ADD COLUMN IF NOT EXISTS body TEXT;
