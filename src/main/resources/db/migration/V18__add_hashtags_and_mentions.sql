-- Hashtags and mentions, re-added in a lighter form than V5's (dropped in V13). A hashtag is just
-- a lowercase tag on a post — no separate hashtags table to keep in sync — indexed from the
-- caption whenever a post is created or its caption edited. Mentions need no table: they only
-- produce notifications, computed from the text at write time.
CREATE TABLE IF NOT EXISTS post_hashtags (
    post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    tag VARCHAR(50) NOT NULL,
    PRIMARY KEY (post_id, tag)
);
CREATE INDEX IF NOT EXISTS idx_post_hashtags_tag ON post_hashtags (tag, post_id);

-- Backfill from existing captions. Mirrors TextEntities.extractHashtags: 1-50 word characters,
-- at least one letter (so "#1" isn't a tag), stored lowercase.
INSERT INTO post_hashtags (post_id, tag)
SELECT DISTINCT p.id, lower(m[1])
  FROM posts p, regexp_matches(coalesce(p.caption, ''), '#([[:alnum:]_]{1,50})', 'g') AS m
 WHERE m[1] ~ '[[:alpha:]]'
ON CONFLICT DO NOTHING;

-- Same drop-whatever-CHECK-exists pattern as V15, extended with the two mention types.
DO $$
DECLARE
    ck RECORD;
BEGIN
    FOR ck IN
        SELECT c.conname
          FROM pg_constraint c
          JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
         WHERE c.contype = 'c'
           AND c.conrelid = 'notifications'::regclass
           AND a.attname = 'type'
    LOOP
        EXECUTE format('ALTER TABLE notifications DROP CONSTRAINT %I', ck.conname);
    END LOOP;
END $$;

ALTER TABLE notifications ALTER COLUMN type TYPE VARCHAR(30);
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('LIKE', 'COMMENT', 'FOLLOW', 'FOLLOW_REQUEST', 'FOLLOW_REQUEST_ACCEPTED',
                    'MENTION_POST', 'MENTION_COMMENT'));
