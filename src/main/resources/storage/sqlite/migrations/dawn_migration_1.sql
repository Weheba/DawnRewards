CREATE TABLE IF NOT EXISTS dawnrewards_migrations (
    version    INTEGER NOT NULL PRIMARY KEY,
    checksum   TEXT    NOT NULL,
    applied_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS dawnrewards_players (
    uuid           TEXT    NOT NULL PRIMARY KEY,
    current_day    INTEGER NOT NULL DEFAULT 0,
    current_streak INTEGER NOT NULL DEFAULT 0,
    highest_streak INTEGER NOT NULL DEFAULT 0,
    last_claim_at  INTEGER
);
