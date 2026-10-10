-- Message requests: a 1:1 conversation started by someone the recipient doesn't follow is PENDING
-- (shown in the recipient's Requests folder) until the recipient accepts or replies. Every
-- existing conversation stays ACCEPTED. Idempotent because prod was baselined at V12.
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS request_status VARCHAR(10) NOT NULL DEFAULT 'ACCEPTED';
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS initiator_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_conversations_request_status ON conversations (request_status);
