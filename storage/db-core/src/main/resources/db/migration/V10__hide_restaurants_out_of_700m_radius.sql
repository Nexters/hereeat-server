UPDATE t_restaurant r
SET is_display = false,
    updated_at = NOW()
FROM t_region reg
WHERE r.region_id = reg.id
  AND r.deleted_at IS NULL
  AND r.is_display = true
  AND ST_Distance(
          r.location::geography,
          ST_SetSRID(ST_MakePoint(reg.longitude, reg.latitude), 4326)::geography
      ) > 700;
