UPDATE dawnrewards_players
SET current_day = ?, current_streak = ?, highest_streak = ?, last_claim_at = ?
WHERE uuid = ?
  AND (last_claim_at IS NULL OR last_claim_at <= ?)
