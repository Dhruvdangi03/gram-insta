-- Block and mute relations between users. V7's user_moderation table was dropped in V13; these are
-- the replacement, split in two because the semantics differ: a block is enforced in both
-- directions (neither side can see or reach the other), a mute only hides the muted account's
-- posts, reels and stories from the muter's feeds. IF NOT EXISTS keeps this safe on the
-- Hibernate-built production schema (see application.yml's flyway baseline note).
CREATE TABLE IF NOT EXISTS user_blocks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    blocker_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked ON user_blocks (blocked_id);

CREATE TABLE IF NOT EXISTS user_mutes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    muter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    muted_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (muter_id, muted_id),
    CHECK (muter_id <> muted_id)
);
