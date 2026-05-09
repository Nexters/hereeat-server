# QA 검증 리포트

대상 변경: Restaurant `isDisplay` 필드 추가 (architect plan 01_architect_plan.md 기반)

## 검증 결과 요약

| # | 항목 | 우선순위 | 결과 | 위반 수 |
|---|------|---------|------|---------|
| 1 | Domain 순수성 | P0 | PASS | 0 |
| 2 | Record-Entity 매핑 (isDisplay) | P0 | PASS | 0 |
| 3 | Repository 인터페이스-구현 정합성 | P0 | PASS | 0 |
| 4 | @Transactional 경계 | P1 | PASS | 0 |
| 5 | Flyway V8 마이그레이션 | P0 | PASS | 0 |
| 6 | 빌드(./gradlew build -x test) | P0 | SKIP | - |

## 변경 파일 목록 (git diff HEAD~1)

- M apps/admin/src/main/java/com/yogieat/controller/v1/restaurant/request/RestaurantRequest.java
- M apps/api/src/test/java/com/yogieat/region/RegionMigrationIntegrationTest.java
- M apps/domain/src/main/java/com/yogieat/restaurant/domain/CreateRestaurant.java
- M apps/domain/src/main/java/com/yogieat/restaurant/domain/Restaurant.java
- M apps/domain/src/main/java/com/yogieat/restaurant/service/RestaurantCollectionWriteService.java
- M apps/domain/src/main/java/com/yogieat/restaurant/service/RestaurantCommand.java
- M apps/domain/src/test/java/...(다수 테스트)
- M storage/db-core/src/main/java/com/yogieat/datasource/db/core/restaurant/RestaurantCoreRepository.java
- M storage/db-core/src/main/java/com/yogieat/datasource/db/core/restaurant/RestaurantEntity.java
- A storage/db-core/src/main/resources/db/migration/V8__add_is_display_to_restaurant.sql

---

## 항목별 검증 상세

### 1. [P0] Domain 순수성 — PASS

`apps/domain/src/main/java` 전체에서 금지 패턴 검색 결과 0건.

검색 패턴: `jakarta.persistence`, `javax.persistence`, `com.querydsl`, `com.yogieat.datasource`, `org.springframework.data`, `org.hibernate`

- Restaurant.java, CreateRestaurant.java, RestaurantCommand.java 모두 순수 Java + 도메인 모델만 import.
- isDisplay 필드는 박싱 `Boolean` 타입으로 도메인 Record에 안전하게 추가됨.

### 2. [P0] Record-Entity 매핑 — PASS

양쪽 동시 비교 대상:
- 도메인 Record: `apps/domain/.../domain/Restaurant.java`, `CreateRestaurant.java`, `service/RestaurantCommand.java`
- Entity: `storage/db-core/.../restaurant/RestaurantEntity.java`

검증 결과:
- `Restaurant` Record의 `isDisplay` 필드(40번 라인)가 `RestaurantEntity.toDomain` 매핑(265번 라인 `entity.getIsDisplay()`)에 반영됨.
- `CreateRestaurant.isDisplay()`(38번 라인)가 `RestaurantEntity.from(...)` 빌더(229번 라인 `.isDisplay(createRestaurant.isDisplay())`)에 반영됨.
- Entity 빌더 생성자(182번 라인) `this.isDisplay = isDisplay != null ? isDisplay : Boolean.TRUE`로 NOT NULL 제약 위반 가드 처리됨.
- `RestaurantCommand.Patch.isDisplay`(41번 라인)가 `RestaurantEntity.applyAdminPatch(...)` (421-423번 라인)에서 null 체크 후 반영됨.
- 엔티티 컬럼 `@Column(name = "is_display", nullable = false)` 마이그레이션과 일치.

호출자 회귀 검증:
- `RestaurantCollectionWriteService.java:78` — `Boolean.TRUE` 전달 (수집 신규 = 노출).
- `CreateRestaurant.fromKakaoPlaceDetail`(153번 라인) — `null` 전달 후 Entity에서 true 보정.
- `RestaurantFixture` 픽스처 두 개 모두 `true` 디폴트 설정됨.
- `RegionMigrationIntegrationTest:368` 테스트 컨스트럭터 호출에 `Boolean.TRUE` 추가됨.

### 3. [P0] Repository 인터페이스-구현 정합성 — PASS

`RestaurantCoreRepository` (storage/db-core)는 `implements RestaurantRepository`(apps/domain) 선언 유지.
- `applyAdminPatch(Long, RestaurantCommand.Patch)` 시그니처(인터페이스 55번 라인)와 구현(199번 라인) 일치.
- `toRecommendationCandidate`(537번 라인 `tuple.get(restaurantEntity.isDisplay)`)에서 새 isDisplay 필드를 Record 27번째 컴포넌트로 정확히 매핑.
- `toDomain` 위임이 모두 `RestaurantEntity.toDomain`을 통과해 isDisplay가 일관되게 채워짐.

### 4. [P1] @Transactional 경계 — PASS

CoreRepository의 변경 메서드(`applyAdminPatch`, `batchApplySyncPatch`, `batchDeleteByIds`, `applySyncPatch`) 모두 `@Transactional` 부착됨. 본 변경에서 트랜잭션 경계 변경 없음.

### 5. [P0] Flyway V8 마이그레이션 — PASS

파일: `storage/db-core/src/main/resources/db/migration/V8__add_is_display_to_restaurant.sql`

```sql
ALTER TABLE t_restaurant
    ADD COLUMN is_display BOOLEAN NOT NULL DEFAULT TRUE;
```

검증:
- 네이밍 컨벤션(V7__add_phone_number_to_restaurant.sql 패턴)과 일치.
- V0~V7 기존 파일과 충돌 없음. V8은 신규 버전.
- `is_display` 컬럼은 V0 베이스라인 및 V1~V7에 존재하지 않음(grep 0건).
- PostgreSQL 표준 문법(BOOLEAN, DEFAULT TRUE, NOT NULL). 기존 행은 DEFAULT TRUE로 채워져 NOT NULL 제약 위반 없음.
- Entity `@Column(name = "is_display", nullable = false)` 정의와 일치.

### 6. [P0] 빌드 검증 — SKIP

`./gradlew build -x test` 실행 시도. 결과:

```
Cannot find a Java installation on your machine matching: {languageVersion=25, ...}
Toolchain download repositories have not been configured.
```

원인: 본 환경에 JDK 25가 설치되어 있지 않음(JDK 21만 가용). `build.gradle:33`은 `languageVersion = JavaLanguageVersion.of(25)`로 고정되어 있고 toolchain 자동 다운로드 비활성. 컴파일 검증을 수행할 수 없음.

권장 조치: JDK 25를 설치하거나 `org.gradle.toolchains.foojay-resolver-convention` 플러그인을 통한 자동 다운로드를 활성화한 환경에서 재실행.

정적 코드 검증 차원에서는 모든 호출자(`CreateRestaurant.of`, `CreateRestaurant` 컨스트럭터 직접 호출, `RestaurantEntity` 빌더, `RestaurantCommand.Patch` 컨스트럭터, `RestaurantFixture`)가 새 컴포넌트 추가에 맞춰 갱신되었음을 확인했으며, 컴파일 에러 후보(미갱신 호출부) 0건.

---

## 부수 관찰

- `RestaurantRequest.Patch.toCommand` (admin)는 `isDisplay`에 의도적으로 `null`을 전달하고 주석으로 "운영자 노출 토글은 별도 PR에서 도입 예정"이라고 명시. 후속 PR에서 Admin Request에 `isDisplay` 필드를 추가하면 자연스럽게 연결됨. 본 PR 범위 내에서는 의도한 동작.
- `applySyncPatch`는 의도적으로 isDisplay를 건드리지 않음 — sync 동기화에 노출 여부를 반영하지 않는다는 architect plan 권장사항과 일치.
- `RestaurantCoreRepository.findAdminRestaurantDetailById` 결과 DTO `RestaurantAdminResult.Detail`에는 isDisplay가 포함되지 않음. 추후 어드민 상세 조회에서 노출 토글 상태를 보여주려면 별도 작업 필요(현 요구사항 범위 외).

## 최종 판정: PASS (단, 빌드 검증은 환경 제약으로 SKIP)

정적 분석 기준 모든 P0/P1 항목 통과. 빌드 컴파일은 JDK 25 가용 환경에서 별도 확인 필요.
