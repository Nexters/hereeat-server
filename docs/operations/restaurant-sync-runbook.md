# Restaurant Sync 실행 가이드 (전체 + NULL 우선 단건)

> 최종 업데이트: 2026-03-09

## 1) API/BATCH 재빌드 + 재기동

코드 반영 후 반드시 API/BATCH를 재기동한다.

```bash
./gradlew :apps:api:bootJar :batch:sync:bootJar
```

Docker Compose 환경이면 최신 이미지로 재배포한다.

```bash
cd docker
docker compose up -d --build yogieat-api yogieat-batch-sync
```

## 2) 전체 보정 1회 실행

```bash
curl -X POST http://localhost:8080/api/v1/restaurants/sync-jobs/all
```

응답으로 받은 `jobId`를 조회해 완료 상태를 확인한다.

```bash
curl http://localhost:8080/api/v1/restaurants/sync-jobs/{jobId}
```

터미널 상태:
- `SUCCESS`
- `PARTIAL_FAILED`
- `FAILED`

## 3) 남은 NULL 대상만 단건 보정 실행

우선순위 조회 SQL:
- `scripts/sync/sql/priority-null-restaurants.sql`

자동 단건 보정 스크립트 실행:

```bash
export DATASOURCE_DB_CORE_JDBC_URL=jdbc:postgresql://localhost:5432/yogieat
export DATASOURCE_DB_CORE_USERNAME=postgres
export DATASOURCE_DB_CORE_PASSWORD=postgres

API_BASE_URL=http://localhost:8080/api/v1 \
PRIORITY_LIMIT=100 \
scripts/sync/run-single-sync-for-priority-nulls.sh
```

## 4) 확인 SQL

남은 NULL 건수 확인:

```sql
select count(*) as remaining_null_targets
from t_restaurant
where deleted_at is null
  and (
    ai_mate_summary_title is null
    or ai_mate_summary_contents is null
    or represent_menu is null
    or represent_menu_price is null
    or review_count is null
    or blog_review_count is null
    or price_level is null
    or time_slot is null
    or map_url is null
    or "location" is null
  or image_url is null
  or representative_review is null
  );
```

## 5) 관리자 검색 인덱스 반영 (PostgreSQL)

관리자 맛집 목록 검색(키워드 containsIgnoreCase)에 대한 성능 개선을 위해 트라이그램 인덱스를 적용한다.

```bash
psql "$DATASOURCE_DB_CORE_JDBC_URL" -v ON_ERROR_STOP=1 -f scripts/sync/sql/restaurant-admin-search-index-up.sql
```

배포 후 통계 갱신:

```sql
ANALYZE t_restaurant;
```

롤백이 필요한 경우:

```bash
psql "$DATASOURCE_DB_CORE_JDBC_URL" -v ON_ERROR_STOP=1 -f scripts/sync/sql/restaurant-admin-search-index-down.sql
```
