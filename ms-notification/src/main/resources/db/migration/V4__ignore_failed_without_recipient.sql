UPDATE sent_notifications
SET status = 'IGNORED',
    error = 'IGNORED_MISSING_RECIPIENT',
    sent_at = NOW()
WHERE status = 'FAILED'
  AND (recipient IS NULL OR recipient = '');
