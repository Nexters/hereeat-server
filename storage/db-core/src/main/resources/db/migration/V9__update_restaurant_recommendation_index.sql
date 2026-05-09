DROP INDEX IF EXISTS idx_restaurant_active_region_category_time_slot;

CREATE INDEX IF NOT EXISTS idx_restaurant_active_region_category_time_slot
    ON t_restaurant(region_id, category_id, time_slot)
    WHERE deleted_at IS NULL
      AND is_display = TRUE;
