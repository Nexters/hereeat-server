-- Rollback for restaurant-admin-search-index-up.sql
DROP INDEX IF EXISTS idx_t_restaurant_name_trgm_active;
DROP INDEX IF EXISTS idx_t_restaurant_address_trgm_active;
DROP INDEX IF EXISTS idx_t_restaurant_external_id_trgm_active;
