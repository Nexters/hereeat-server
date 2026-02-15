#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080/api/v1}"
PRIORITY_LIMIT="${PRIORITY_LIMIT:-100}"

JDBC_URL="${DATASOURCE_DB_CORE_JDBC_URL:-}"
DB_USER="${DATASOURCE_DB_CORE_USERNAME:-}"
DB_PASS="${DATASOURCE_DB_CORE_PASSWORD:-}"

if [[ -z "$JDBC_URL" || -z "$DB_USER" || -z "$DB_PASS" ]]; then
  echo "ERROR: DATASOURCE_DB_CORE_JDBC_URL / DATASOURCE_DB_CORE_USERNAME / DATASOURCE_DB_CORE_PASSWORD are required."
  exit 1
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "ERROR: psql not found"
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl not found"
  exit 1
fi

DB_HOST_PORT_DB="${JDBC_URL#jdbc:postgresql://}"
DB_HOST_PORT="${DB_HOST_PORT_DB%%/*}"
DB_NAME="${DB_HOST_PORT_DB#*/}"
DB_HOST="${DB_HOST_PORT%%:*}"
DB_PORT="${DB_HOST_PORT##*:}"

if [[ -z "$DB_HOST" || -z "$DB_PORT" || -z "$DB_NAME" ]]; then
  echo "ERROR: invalid DATASOURCE_DB_CORE_JDBC_URL format: $JDBC_URL"
  exit 1
fi

SQL="
with target as (
    select
        r.id,
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
          + case when r.\"location\" is null then 1 else 0 end
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
          + case when r.\"location\" is null then 1 else 0 end
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
        or r.\"location\" is null
        or r.image_url is null
        or r.representative_review is null
      )
)
select id
from target
order by missing_score desc, missing_count desc, id asc
limit ${PRIORITY_LIMIT};
"

echo "Fetching priority NULL targets (limit=${PRIORITY_LIMIT})..."
TARGET_IDS="$(
  PGPASSWORD="$DB_PASS" psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -At \
    -c "$SQL"
)"

if [[ -z "${TARGET_IDS//$'\n'/}" ]]; then
  echo "No priority NULL targets found."
  exit 0
fi

submitted=0
conflicts=0
failed=0

while IFS= read -r id; do
  [[ -z "$id" ]] && continue

  url="${API_BASE_URL}/restaurants/${id}/sync-jobs"
  http_code="$(curl -sS -o /tmp/yogieat-single-sync-response.json -w "%{http_code}" -X POST "$url")"

  case "$http_code" in
    200|201|202)
      submitted=$((submitted + 1))
      ;;
    409)
      conflicts=$((conflicts + 1))
      ;;
    *)
      failed=$((failed + 1))
      echo "Failed to submit single sync job: restaurantId=${id}, status=${http_code}"
      ;;
  esac
done <<< "$TARGET_IDS"

echo "Done."
echo "submitted=${submitted}, conflicts=${conflicts}, failed=${failed}"
