SELECT id, player_name, cosmetic, delivery_id, status, attempts, created_at, expires_at, next_attempt_at, updated_at, last_error
FROM dawnrewards_cosmetic_grants
WHERE status = 'PENDING' AND next_attempt_at <= ?
ORDER BY next_attempt_at
LIMIT ?
