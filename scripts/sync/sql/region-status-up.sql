-- Region status migration (PostgreSQL)
-- Run once before deploying application code that maps t_region.status.

ALTER TABLE t_region
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

UPDATE t_region
SET status = CASE
    WHEN is_active = TRUE THEN 'ACTIVE'
    ELSE 'INACTIVE'
END
WHERE status IS NULL;

ALTER TABLE t_region
    ALTER COLUMN status SET NOT NULL;

DROP INDEX IF EXISTS idx_region_active_sort_order;

CREATE INDEX IF NOT EXISTS idx_region_status_sort_order
    ON t_region (status, sort_order);

ALTER TABLE t_region
    DROP COLUMN IF EXISTS is_active;
