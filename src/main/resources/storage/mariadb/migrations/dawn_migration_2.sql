CREATE TABLE IF NOT EXISTS dawnrewards_cosmetic_grants (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    player_key      VARCHAR(32)  NOT NULL,
    player_name     VARCHAR(32)  NOT NULL,
    cosmetic        VARCHAR(128) NOT NULL,
    delivery_id     VARCHAR(96)  NOT NULL DEFAULT '',
    status          VARCHAR(16)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    created_at      BIGINT       NOT NULL,
    expires_at      BIGINT       NOT NULL,
    next_attempt_at BIGINT       NOT NULL,
    updated_at      BIGINT       NOT NULL,
    last_error      VARCHAR(512) NULL,
    UNIQUE KEY dawnrewards_cosmetic_grants_key (player_key, cosmetic, delivery_id),
    KEY dawnrewards_cosmetic_grants_due (status, next_attempt_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin;
