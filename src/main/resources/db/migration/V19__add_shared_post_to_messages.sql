-- SET NULL (not CASCADE like saved_posts/comments) so deleting the shared post doesn't delete
-- the message/conversation history it was shared in — the bubble just loses its preview.
ALTER TABLE messages ADD COLUMN shared_post_id BIGINT REFERENCES posts(id) ON DELETE SET NULL;
