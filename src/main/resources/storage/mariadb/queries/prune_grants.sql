DELETE FROM dawnrewards_cosmetic_grants
WHERE status = 'GRANTED' AND updated_at < ?
