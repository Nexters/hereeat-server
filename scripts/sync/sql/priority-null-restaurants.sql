-- Active restaurant rows with priority score for NULL-field backfill.
-- Usage:
--   psql "$DATABASE_URL" -f scripts/sync/sql/priority-null-restaurants.sql
-- or
--   psql -h ... -U ... -d ... -v limit=100 -f scripts/sync/sql/priority-null-restaurants.sql

\set limit 100

with target as (
    select
        r.id,
        r.external_id,
        r.name,
        r.region,
        (
            case when r.ai_mate_summary_title is null then 3 else 0 end
          + case when r.ai_mate_summary_contents is null then 3 else 0 end
          + case when r.represent_menu is null then 2 else 0 end
          + case when r.represent_menu_price is null then 2 else 0 end
          + case when r.review_count is null then 2 else 0 end
          + case when r.blog_review_count is null then 2 else 0 end
          + case when r.price_level is null then 1 else 0 end
          + case when r.time_slot is null then 1 else 0 end
          + case when r.map_url is null then 1 else 0 end
          + case when r."location" is null then 1 else 0 end
          + case when r.image_url is null then 1 else 0 end
          + case when r.representative_review is null then 1 else 0 end
        ) as missing_score,
        (
            case when r.ai_mate_summary_title is null then 1 else 0 end
          + case when r.ai_mate_summary_contents is null then 1 else 0 end
          + case when r.represent_menu is null then 1 else 0 end
          + case when r.represent_menu_price is null then 1 else 0 end
          + case when r.review_count is null then 1 else 0 end
          + case when r.blog_review_count is null then 1 else 0 end
          + case when r.price_level is null then 1 else 0 end
          + case when r.time_slot is null then 1 else 0 end
          + case when r.map_url is null then 1 else 0 end
          + case when r."location" is null then 1 else 0 end
          + case when r.image_url is null then 1 else 0 end
          + case when r.representative_review is null then 1 else 0 end
        ) as missing_count
    from t_restaurant r
    where r.deleted_at is null
      and (
        r.ai_mate_summary_title is null
        or r.ai_mate_summary_contents is null
        or r.represent_menu is null
        or r.represent_menu_price is null
        or r.review_count is null
        or r.blog_review_count is null
        or r.price_level is null
        or r.time_slot is null
        or r.map_url is null
        or r."location" is null
        or r.image_url is null
        or r.representative_review is null
      )
)
select
    id,
    external_id,
    name,
    region,
    missing_score,
    missing_count
from target
order by missing_score desc, missing_count desc, id asc
limit :limit;
