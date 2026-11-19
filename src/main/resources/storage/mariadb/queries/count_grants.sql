SELECT status, COUNT(*) AS total
FROM dawnrewards_cosmetic_grants
GROUP BY status
