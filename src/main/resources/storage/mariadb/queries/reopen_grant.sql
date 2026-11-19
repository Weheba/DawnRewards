UPDATE dawnrewards_cosmetic_grants
SET status = 'PENDING', attempts = 0, expires_at = ?, next_attempt_at = ?, updated_at = ?, last_error = NULL
WHERE id = ? AND status IN ('FAILED', 'EXPIRED')
