UPDATE dawnrewards_players
SET current_day = 0, current_streak = 0, last_claim_at = NULL
WHERE uuid = ?
