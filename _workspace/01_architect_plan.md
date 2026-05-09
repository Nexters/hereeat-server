# 구현 계획: Restaurant isDisplay 필드 추가

## 요구사항 요약
Restaurant 도메인에 노출 여부를 제어하는 `isDisplay`(boolean) 필드를 추가한다. JPA Entity(`RestaurantEntity`)와 도메인 Record(`Restaurant`, `CreateRestaurant`)에 필드를 추가하고, `t_restaurant` 테이블에 `is_display BOOLEAN` 컬럼을 추가하는 Flyway 마이그레이션(V8)을 작성한다. 기본값은 노출(true)로 설정한다.

## 영향 모듈
- [x] apps/domain — Restaurant / CreateRestaurant Record에 `isDisplay` 필드 추가, RestaurantCommand.Patch에 필드 추가, 관련 빌더/팩토리 갱신
- [x] storage/db-core — RestaurantEntity에 `isDisplay` 컬럼 매핑 추가, V8 Flyway 마이그레이션 추가, Entity ↔ Domain 매핑 갱신
- [ ] apps/api — 변경 없음 (현재 요구사항은 Entity/스키마 한정)
- [ ] apps/admin — (선택) 어드민에서 토글이 필요하면 별도 작업으로 분리 권장

> 주의: 현재 요구사항은 "Entity 필드 + 컬럼" 추가 범위로 해석함. 어드민 노출/조회 API에 노출 여부를 반영하려면 후속 작업 필요. Admin Patch에 반영하려면 `RestaurantCommand.Patch` 및 `applyAdminPatch` 함께 갱신 권장(아래 권장 변경에 포함).

## 모듈별 생성/수정 파일

### apps/domain
| 파일 경로 | 타입 | 설명 |
|----------|------|------|
| com.yogieat.restaurant.domain.Restaurant | 수정 | Record에 `Boolean isDisplay` 컴포넌트 추가 (마지막 필드 끝에 append) |
| com.yogieat.restaurant.domain.CreateRestaurant | 수정 | Record 컴포넌트에 `Boolean isDisplay` 추가, `of(...)` / `fromKakaoPlaceDetail(...)` 팩토리 시그니처에 반영 (기본 true 매핑) |
| com.yogieat.restaurant.service.RestaurantCommand | 수정 (권장) | `Patch` Record에 `Boolean isDisplay` 추가, `Patch.empty()` 갱신 |
| com.yogieat.restaurant.sync.domain.RestaurantSyncPatch | 검토 | sync 동기화에 노출 여부를 반영할지 결정 — 현재 요구사항 범위 외, 변경 없음 |
| com.yogieat.restaurant.fixture.RestaurantFixture (test) | 수정 | 테스트 픽스처 빌더/디폴트값에 `isDisplay = true` 추가 |
| 호출부 컴파일 영향: `CreateRestaurant.of(...)`/`fromKakaoPlaceDetail(...)` 호출처 (e.g. `RestaurantCollectionWriteService`, `KakaoPlaceMapperImpl`, 관련 테스트) | 수정 | 새 파라미터 전달 (기본 `true`) |

### storage/db-core
| 파일 경로 | 타입 | 설명 |
|----------|------|------|
| com.yogieat.datasource.db.core.restaurant.RestaurantEntity | 수정 | `@Column(name = "is_display", nullable = false) private Boolean isDisplay;` 필드 추가, 빌더 파라미터 추가, `from(CreateRestaurant, ...)` 매핑에 `isDisplay` 반영 (null 시 true 디폴트), `toDomain(...)` 변환에 `isDisplay` 추가, 필요 시 `applyAdminPatch`에 반영 |
| storage/db-core/src/main/resources/db/migration/V8__add_is_display_to_restaurant.sql | 생성 | `ALTER TABLE t_restaurant ADD COLUMN is_display BOOLEAN NOT NULL DEFAULT TRUE;` |
| com.yogieat.datasource.db.core.restaurant.RestaurantCoreRepository | 검토 | 노출 필터링 쿼리가 필요한 경우만 변경. 현재 요구사항 범위 외 → 변경 없음 |

### apps/api / apps/admin
| 파일 경로 | 타입 | 설명 |
|----------|------|------|
| (없음) | - | 본 요구사항은 컨트롤러/Facade 변경 없음. Admin Patch 노출 토글이 필요하면 후속 PR로 `RestaurantPatchRequest` 및 `RestaurantAdminFacade` 갱신. |

## 구현 순서
1. apps/domain: `Restaurant` Record에 `isDisplay` 추가
2. apps/domain: `CreateRestaurant` Record + `of(...)` / `fromKakaoPlaceDetail(...)` 갱신 (기본 `true`)
3. apps/domain: `RestaurantCommand.Patch` 갱신 (권장)
4. storage/db-core: `V8__add_is_display_to_restaurant.sql` 마이그레이션 추가
5. storage/db-core: `RestaurantEntity` 필드/빌더/`from`/`toDomain`/`applyAdminPatch` 갱신
6. 호출부 컴파일 에러 해소 (`CreateRestaurant` 팩토리 호출자, fixture 등)
7. 테스트: `RestaurantFixture`, `RestaurantServiceTest`, `RestaurantSyncJobCoreRepositoryTest` 등에서 `isDisplay` 디폴트 기대값 추가

## 의존성 방향 확인
- apps:domain 변경 (`Restaurant`, `CreateRestaurant`, `RestaurantCommand`)은 외부 모듈 의존 없이 Record/POJO 수정 → 허용 의존성(spring-context, jackson, slf4j) 범위 내. 새 의존성 추가 없음. ✅
- storage:db-core → apps:domain의 `Restaurant`, `CreateRestaurant`, `RestaurantCommand` 도메인 모델만 참조 (현재와 동일). 역방향 의존 없음. ✅
- 마이그레이션은 storage/db-core/src/main/resources/db/migration 하위에만 위치. 기존 컨벤션(V1~V7) 준수. ✅
- Entity 빌더의 `isDisplay` 매핑은 null 가드 처리하여 신규 호출자가 누락해도 컬럼 NOT NULL 위반이 발생하지 않도록 한다 (`isDisplay != null ? isDisplay : Boolean.TRUE`). ✅

## 참고한 기존 패턴
- Flyway 마이그레이션 네이밍/스타일: `storage/db-core/src/main/resources/db/migration/V7__add_phone_number_to_restaurant.sql` (단일 ALTER TABLE ADD COLUMN)
- Entity 컬럼 추가 패턴: `RestaurantEntity.phoneNumber`, `RestaurantEntity.station` (필드 + `@Column` + 빌더 파라미터 + `from(...)` 매핑 + `toDomain(...)` 매핑)
- Domain Record 확장 패턴: `Restaurant`, `CreateRestaurant` 의 `phoneNumber` 추가 이력
- Admin Patch 패턴: `RestaurantCommand.Patch` + `RestaurantEntity.applyAdminPatch(...)` (필요 시 `isDisplay` 반영)
- 도메인 모델 ↔ 엔티티 매핑 위치: `RestaurantEntity.from(CreateRestaurant, Long)` 및 `RestaurantEntity.toDomain(RestaurantEntity, Region)` 정적 메서드

## 권장/주의 사항
- 컬럼 디폴트 `TRUE`로 두어 기존 행은 자동 노출 처리되도록 한다.
- Java 필드 타입은 `Boolean`(박싱)으로 두되 Entity 빌더에서 null이면 `true`로 보정하여 NPE 및 NOT NULL 제약 위반을 방지한다. Record는 `Boolean`을 그대로 사용해 호출자 호환성을 유지한다.
- Sync 패치(`RestaurantSyncPatch`)와 외부 매퍼(`KakaoPlaceMapperImpl`)에 `isDisplay`를 노출하지 않는다 (운영자 제어 필드로 한정 권장).
