-- FOLLOW_REQUEST and FOLLOW_REQUEST_ACCEPTED were added to NotificationType without touching this
-- table, so every insert of either failed the CHECK constraint (and FOLLOW_REQUEST_ACCEPTED, at 23
-- characters, would also have overflowed VARCHAR(20)). NotificationConsumer catches and ACKs insert
-- failures, so it surfaced as notifications silently never appearing rather than as an error.
ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ALTER COLUMN type TYPE VARCHAR(30);
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('LIKE', 'COMMENT', 'FOLLOW', 'FOLLOW_REQUEST', 'FOLLOW_REQUEST_ACCEPTED'));
