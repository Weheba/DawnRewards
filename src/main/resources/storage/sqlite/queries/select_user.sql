SELECT current_day, current_streak, highest_streak, last_claim_at
FROM dawnrewards_players
WHERE uuid = ?
