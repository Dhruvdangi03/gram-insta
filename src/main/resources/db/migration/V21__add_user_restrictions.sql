-- Restrict: a silent, one-sided soft limit. The restricted user's comments on the restrictor's
-- posts stay hidden from everyone but the author and the post owner until the owner approves
-- them, and their DMs land in the restrictor's message requests. Idempotent because prod was
-- baselined at V12.
CREATE TABLE IF NOT EXISTS user_restrictions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    restrictor_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    restricted_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (restrictor_id, restricted_id),
    CHECK (restrictor_id <> restricted_id)
);
CREATE INDEX IF NOT EXISTS idx_user_restrictions_restricted ON user_restrictions (restricted_id);

-- false = a restricted user's comment awaiting the post owner's approval. Existing comments stay public.
ALTER TABLE comments ADD COLUMN IF NOT EXISTS approved BOOLEAN NOT NULL DEFAULT TRUE;
