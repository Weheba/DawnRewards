CREATE TABLE IF NOT EXISTS dawnrewards_cosmetic_grants (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    player_key      TEXT    NOT NULL,
    player_name     TEXT    NOT NULL,
    cosmetic        TEXT    NOT NULL,
    delivery_id     TEXT    NOT NULL DEFAULT '',
    status          TEXT    NOT NULL,
    attempts        INTEGER NOT NULL DEFAULT 0,
    created_at      INTEGER NOT NULL,
    expires_at      INTEGER NOT NULL,
    next_attempt_at INTEGER NOT NULL,
    updated_at      INTEGER NOT NULL,
    last_error      TEXT,
    UNIQUE (player_key, cosmetic, delivery_id)
);

CREATE INDEX IF NOT EXISTS dawnrewards_cosmetic_grants_due
    ON dawnrewards_cosmetic_grants (status, next_attempt_at);
