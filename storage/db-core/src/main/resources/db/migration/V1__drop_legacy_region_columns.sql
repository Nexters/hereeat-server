DROP
    INDEX IF EXISTS idx_restaurant_region;

DROP
    INDEX IF EXISTS idx_restaurant_region_deleted_at;

ALTER TABLE
    t_restaurant DROP
        COLUMN IF EXISTS region;

ALTER TABLE
    t_gathering DROP
        COLUMN IF EXISTS region;
