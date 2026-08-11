-- Restaurant external_id soft-delete-aware uniqueness migration (PostgreSQL)
-- t_restaurant soft-deletes rows (deleted_at), so a table-wide UNIQUE(external_id)
-- blocks re-collecting a Kakao place that was previously removed: the dead row
-- still occupies the external_id even though every app-side duplicate check
-- filters on deleted_at IS NULL.
--
-- Replace the table-wide constraint with a partial unique index scoped to
-- active rows. Run statements one at a time (not inside a transaction block) —
-- CREATE INDEX CONCURRENTLY cannot run inside one, and creating the new index
-- BEFORE dropping the old constraint means uniqueness among active rows is
-- never left unenforced.
--
-- Current live constraint name observed on Supabase (Hibernate ddl-auto
-- auto-generated it, so it does not match the friendly name in V0__baseline.sql):
--   ukbr3s4qledlq71iqfyd89fablt
-- Both names are dropped defensively in case an environment has the other one.

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_restaurant_external_id_active
    ON t_restaurant (external_id)
    WHERE deleted_at IS NULL;

ALTER TABLE t_restaurant
    DROP CONSTRAINT IF EXISTS ukbr3s4qledlq71iqfyd89fablt;

ALTER TABLE t_restaurant
    DROP CONSTRAINT IF EXISTS uk_restaurant_external_id;

-- Post-check: should return 0 rows before and after this migration.
-- SELECT external_id, count(*) FROM t_restaurant
-- WHERE deleted_at IS NULL GROUP BY external_id HAVING count(*) > 1;
