-- Hibernate (spring.jpa.hibernate.ddl-auto=update at some point) added its own foreign keys next
-- to the ones these migrations create, named like "fkh4c7lvsc298whoyd4w9ta25cr" and with no
-- ON DELETE CASCADE. Those duplicates block deleting a post that still has comments/media/saves,
-- even though the migration-created keys would cascade. Drop every Hibernate-named foreign key,
-- and if one ever covered a column that has no migration-created key, recreate it with CASCADE
-- (the same rule every foreign key in V1–V12 follows).
DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT c.oid,
               c.conname,
               c.conrelid::regclass AS tbl,
               c.confrelid::regclass AS ref_tbl,
               c.conkey,
               c.confkey,
               (SELECT string_agg(quote_ident(a.attname), ', ' ORDER BY k.ord)
                  FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
                  JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum) AS cols,
               (SELECT string_agg(quote_ident(a.attname), ', ' ORDER BY k.ord)
                  FROM unnest(c.confkey) WITH ORDINALITY AS k(attnum, ord)
                  JOIN pg_attribute a ON a.attrelid = c.confrelid AND a.attnum = k.attnum) AS ref_cols
          FROM pg_constraint c
          JOIN pg_namespace n ON n.oid = c.connamespace
         WHERE c.contype = 'f'
           AND n.nspname = current_schema()
           AND c.conname ~ '^fk[a-z0-9]{20,}$'
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', fk.tbl, fk.conname);

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint o
             WHERE o.contype = 'f'
               AND o.conrelid = fk.tbl
               AND o.conkey = fk.conkey
               AND o.confrelid = fk.ref_tbl
        ) THEN
            EXECUTE format('ALTER TABLE %s ADD FOREIGN KEY (%s) REFERENCES %s (%s) ON DELETE CASCADE',
                           fk.tbl, fk.cols, fk.ref_tbl, fk.ref_cols);
        END IF;
    END LOOP;
END $$;
