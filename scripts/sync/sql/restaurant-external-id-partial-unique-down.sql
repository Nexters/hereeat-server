-- Rollback for restaurant-external-id-partial-unique-up.sql
--
-- NOTE: this rollback is only safe if no soft-deleted row currently shares an
-- external_id with another row. Once the app has been running with the
-- partial index (and reviving soft-deleted rows instead of inserting new
-- ones), that invariant may no longer hold. Check first:
--   SELECT external_id, count(*) FROM t_restaurant
--   GROUP BY external_id HAVING count(*) > 1;
-- If that returns any rows, resolve them before adding the constraint back.

ALTER TABLE t_restaurant
    ADD CONSTRAINT uk_restaurant_external_id UNIQUE (external_id);

DROP INDEX CONCURRENTLY IF EXISTS uk_restaurant_external_id_active;
