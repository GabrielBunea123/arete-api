CREATE TABLE users (
    id             BIGSERIAL    PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    full_name      VARCHAR(100) NOT NULL,
    -- NULL for accounts that only sign in through an external provider
    password_hash  VARCHAR(255),
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP,
    version        BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE user_role (
    user_id BIGINT       NOT NULL,
    role    VARCHAR(255) NOT NULL,

    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE user_identity (
    id               BIGSERIAL    PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(255) NOT NULL,
    -- Stable subject id from the provider (Google "sub"); accounts are matched on this, never on email
    provider_subject VARCHAR(255) NOT NULL,
    email            VARCHAR(255),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP,
    version          BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_user_identity_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_identity_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uq_user_identity_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX idx_user_identity_user_id ON user_identity(user_id);

CREATE TABLE refresh_token (
    id             BIGSERIAL   PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    -- SHA-256 hex of the raw token; the raw value only ever lives in the client cookie
    token_hash     VARCHAR(64) NOT NULL,
    -- Shared by every token in one rotation chain, so reuse of a rotated token can revoke the whole chain
    family_id      UUID        NOT NULL,
    expires_at     TIMESTAMP   NOT NULL,
    revoked_at     TIMESTAMP,
    replaced_by_id BIGINT,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP,
    version        BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_refresh_token_user        FOREIGN KEY (user_id)        REFERENCES users(id)         ON DELETE CASCADE,
    CONSTRAINT fk_refresh_token_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES refresh_token(id) ON DELETE SET NULL,
    CONSTRAINT uq_refresh_token_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_token_user_id        ON refresh_token(user_id);
CREATE INDEX idx_refresh_token_family_id      ON refresh_token(family_id);
CREATE INDEX idx_refresh_token_replaced_by_id ON refresh_token(replaced_by_id);
