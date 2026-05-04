CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_gathering_access_key
    ON t_gathering(access_key);

CREATE INDEX IF NOT EXISTS idx_participant_gathering_id_nickname
    ON t_participant(gathering_id, nickname);

CREATE INDEX IF NOT EXISTS idx_restaurant_active_region_category_time_slot
    ON t_restaurant(region_id, category_id, time_slot)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_restaurant_active_updated_at_id_desc
    ON t_restaurant(updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_t_restaurant_name_trgm_active
    ON t_restaurant USING gin (lower(name) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_t_restaurant_address_trgm_active
    ON t_restaurant USING gin (lower(address) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_t_restaurant_external_id_trgm_active
    ON t_restaurant USING gin (lower(external_id) gin_trgm_ops)
    WHERE deleted_at IS NULL;
