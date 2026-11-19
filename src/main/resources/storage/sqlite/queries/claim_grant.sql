UPDATE dawnrewards_cosmetic_grants
SET next_attempt_at = ?
WHERE id = ? AND status = 'PENDING' AND next_attempt_at = ?
