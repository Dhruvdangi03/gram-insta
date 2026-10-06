-- Hashtags, reports and user moderation (block/restrict) were removed from the app. Drop their
-- tables so the schema matches the entities (spring.jpa.hibernate.ddl-auto=validate). The tables
-- created in V5/V7/V8 stay in the migration history; this only removes them going forward.
DROP TABLE IF EXISTS post_hashtags;
DROP TABLE IF EXISTS hashtags;
DROP TABLE IF EXISTS reports;
DROP TABLE IF EXISTS user_moderation;
