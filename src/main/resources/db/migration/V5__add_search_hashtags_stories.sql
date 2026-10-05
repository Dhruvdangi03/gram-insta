CREATE TABLE hashtags (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tag VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE post_hashtags (
    post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    hashtag_id BIGINT NOT NULL REFERENCES hashtags(id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, hashtag_id)
);
CREATE INDEX idx_post_hashtags_hashtag ON post_hashtags (hashtag_id);

-- Photo-only for this phase (see build plan); no media_type/status columns needed since, unlike
-- reels, a story never goes through the async transcode pipeline — it's ready the moment it's
-- created, the same synchronous flow Phase 1 photo posts already use.
CREATE TABLE stories (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_url TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_stories_user_expires ON stories (user_id, expires_at);
