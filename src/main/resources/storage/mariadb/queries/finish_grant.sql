UPDATE dawnrewards_cosmetic_grants
SET status = ?, attempts = ?, next_attempt_at = ?, updated_at = ?, last_error = ?
WHERE id = ? AND status = 'PENDING'
