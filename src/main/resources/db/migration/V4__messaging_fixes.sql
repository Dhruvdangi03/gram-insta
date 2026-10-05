-- messages.is_read was never wired up to any feature (no code path ever set it) — dropped rather
-- than left as a half-built column; add it back properly if/when read receipts are built.
ALTER TABLE messages DROP COLUMN is_read;

-- Backs listConversations' real cursor pagination (replaces a correlated MAX(created_at)
-- subquery ordering with a plain indexed column, updated whenever a message is sent).
ALTER TABLE conversations ADD COLUMN last_message_at TIMESTAMPTZ;
CREATE INDEX idx_conversations_last_message ON conversations (last_message_at DESC, id DESC);
