CREATE TABLE IF NOT EXISTS dawnrewards_migrations (
    version    INT      NOT NULL PRIMARY KEY,
    checksum   CHAR(64) NOT NULL,
    applied_at BIGINT   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS dawnrewards_players (
    uuid           CHAR(36) NOT NULL PRIMARY KEY,
    current_day    INT      NOT NULL DEFAULT 0,
    current_streak INT      NOT NULL DEFAULT 0,
    highest_streak INT      NOT NULL DEFAULT 0,
    last_claim_at  BIGINT   NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
