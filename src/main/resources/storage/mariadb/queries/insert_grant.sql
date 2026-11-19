INSERT IGNORE INTO dawnrewards_cosmetic_grants
    (player_key, player_name, cosmetic, delivery_id, status, attempts, created_at, expires_at, next_attempt_at, updated_at)
VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, ?)
