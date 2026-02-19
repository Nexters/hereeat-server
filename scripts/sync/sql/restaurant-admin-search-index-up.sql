-- Restaurant admin search index migration (PostgreSQL)
-- Improves containsIgnoreCase-based search performance for admin list API:
-- name, address, and external_id are searched with case-insensitive contains.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Trigram GIN indexes for case-insensitive LIKE %% queries on non-deleted rows.
CREATE INDEX IF NOT EXISTS idx_t_restaurant_name_trgm_active
    ON t_restaurant USING gin (lower(name) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_t_restaurant_address_trgm_active
    ON t_restaurant USING gin (lower(address) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_t_restaurant_external_id_trgm_active
    ON t_restaurant USING gin (lower(external_id) gin_trgm_ops)
    WHERE deleted_at IS NULL;
