-- Tracks which stories a viewer has already watched, so StoriesTray can render the "seen" (gray)
-- ring instead of always showing "unseen" (gradient) for every author.
CREATE TABLE story_views (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    viewer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (story_id, viewer_id)
);
CREATE INDEX idx_story_views_viewer ON story_views (viewer_id, story_id);
