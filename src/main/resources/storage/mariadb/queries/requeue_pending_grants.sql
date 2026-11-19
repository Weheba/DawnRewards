UPDATE dawnrewards_cosmetic_grants
SET next_attempt_at = ?
WHERE status = 'PENDING' AND next_attempt_at > ?
