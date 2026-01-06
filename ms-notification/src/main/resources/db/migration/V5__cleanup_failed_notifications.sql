DELETE FROM sent_notifications
WHERE status IN ('FAILED', 'IGNORED');
