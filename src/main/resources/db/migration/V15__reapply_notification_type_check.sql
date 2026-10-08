-- Re-applies V12's fix in a form that is safe on any schema. The production database was built by
-- Hibernate (not V1–V12) and then baselined at version 12, so V12 itself never ran there — and a
-- Hibernate-generated CHECK on notifications.type can predate FOLLOW_REQUEST /
-- FOLLOW_REQUEST_ACCEPTED, which makes those notifications silently fail to insert. Drop whatever
-- CHECK constraints exist on that column, then add the one V12 intended.
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
    CHECK (type IN ('LIKE', 'COMMENT', 'FOLLOW', 'FOLLOW_REQUEST', 'FOLLOW_REQUEST_ACCEPTED'));
