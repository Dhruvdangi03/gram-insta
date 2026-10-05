CREATE TABLE story_highlights (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(50) NOT NULL,
    cover_url VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_story_highlights_user ON story_highlights (user_id, created_at DESC, id DESC);

-- media_url is a denormalized copy, not a FK to stories(id) — StoryCleanupJob hard-deletes expired
-- stories a day after they expire, but a highlight item must survive that purge indefinitely.
CREATE TABLE story_highlight_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    highlight_id BIGINT NOT NULL REFERENCES story_highlights(id) ON DELETE CASCADE,
    media_url VARCHAR(2048) NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_story_highlight_items_highlight ON story_highlight_items (highlight_id, position);
