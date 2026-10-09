-- Content reports, re-added in a lighter form than V8's (dropped in V13): reports can target a
-- post, a comment or a user, carry a fixed reason plus optional free text, and have a status so a
-- reviewer (admin tooling is a later step) can work through them. One report per reporter per
-- target — re-reporting is a no-op rather than a way to inflate the count.
CREATE TABLE IF NOT EXISTS reports (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type VARCHAR(10) NOT NULL CHECK (target_type IN ('POST', 'COMMENT', 'USER')),
    target_id BIGINT NOT NULL,
    reason VARCHAR(20) NOT NULL
        CHECK (reason IN ('SPAM', 'HARASSMENT', 'HATE', 'VIOLENCE', 'NUDITY', 'SCAM', 'SELF_HARM', 'OTHER')),
    details VARCHAR(500),
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'REVIEWED', 'DISMISSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (reporter_id, target_type, target_id)
);
CREATE INDEX IF NOT EXISTS idx_reports_target ON reports (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_reports_status ON reports (status, created_at);
