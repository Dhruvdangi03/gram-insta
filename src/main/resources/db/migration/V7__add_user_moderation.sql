CREATE TABLE user_moderation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(10) NOT NULL CHECK (type IN ('BLOCK','RESTRICT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (actor_id <> target_id),
    UNIQUE (actor_id, target_id, type)
);

CREATE INDEX idx_user_moderation_target ON user_moderation (target_id, type);
