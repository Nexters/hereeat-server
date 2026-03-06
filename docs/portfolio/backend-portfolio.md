# YogiEat Server - 백엔드 포트폴리오 (ybchar 본인 작업 기준)

## 프로젝트 개요

**YogiEat** - 모임 참여자의 선호/불호 투표를 기반으로 맛집을 추천하는 서비스의 백엔드 서버

- **기술 스택**: Java 25, Spring Boot, JPA/QueryDSL, MySQL, Docker Compose, Kakao API, Gemini AI, SSE, Gradle 멀티모듈
- **팀 구성**: 백엔드 2명
- **본인 역할**: 추천 알고리즘 핵심 설계, 멀티모듈 아키텍처, Admin API, 동기화 파이프라인, 인프라/배포

---

## 포트폴리오 항목

### 1. 맛집 추천 알고리즘 설계 및 전략 패턴 기반 리팩토링

참여자들의 선호/불호 투표가 다양한 비율로 들어올 때 공정하고 만족도 높은 Top 3 맛집을 선정해야 하는 문제가 있었다. 단일 서비스 클래스(700줄+)에 점수 계산·카테고리 필터링·Top-K 선정이 모두 섞여있어 테스트와 확장이 어려운 상태였다. `RecommendationProcessor`(오케스트레이션), `RecommendationContextFactory`(투표 집계), `CategoryQuotaSelectionStrategy`(슬롯 배분), `RecommendationScoringPolicy`(가중치 정책)로 책임을 분리하고, Strategy 인터페이스를 도입하여 OCP를 준수하는 구조로 재설계했다. 투표 비율에 따라 카테고리별 슬롯을 최대 나머지 방식(Largest Remainder Method)으로 배분하고, 선호도/신뢰도/거리/AI 요약 부스트 등 8개 가중치를 합산하는 점수 모델을 구현했다. 불호 0표 카테고리를 우선 추천하는 Strict 모드와, Top 3 보장을 위한 2단계 Fallback 선정 로직으로 극단적 투표 분포에서도 빈 결과 없이 추천을 완료하도록 보장했다. 12개 시나리오 테스트(`RecommendationContextFactoryScenarioTest`)와 전략별 단위 테스트를 포함해 총 587줄의 신규 테스트 코드를 작성하여 검증했다.

### 2. 추천 알고리즘 고도화 - Cold Start / Freshness / AI 요약 부스트

신규 등록 맛집이나 최근 업데이트된 맛집이 리뷰 수 부족으로 추천에서 밀려나고, Gemini AI가 생성한 요약 텍스트가 추천 점수에 반영되지 않는 한계가 있었다. 등록 30일 이내 또는 리뷰 10개 미만이면서 평점 4.0 이상인 맛집에 Cold Start 부스트(+0.3)를 부여하고, 데이터 갱신 시점 기준 7일/30일/90일 구간별로 Freshness 점수를 차등 적용했다. AI 요약 텍스트에서 단체석/모임 등 키워드를 감지하여 대규모 모임에 적합한 맛집을 가산하고, 부정 키워드(웨이팅 필수 등)는 감점하는 AI 요약 부스트를 구현했다. 이를 통해 데이터 다양성과 최신성을 반영하는 추천 품질을 확보했다.

### 3. 외부 맛집 데이터 동기화 파이프라인 성능 최적화

Kakao API로 수집한 맛집 데이터를 최신 상태로 유지하면서 API Rate Limit과 서버 리소스를 효율적으로 관리해야 하는 문제가 있었다. 동기화 시 Haversine 공식 기반 반경 1km 지리 필터링(`GeoUtils`)을 적용하여 지역 범위를 벗어난 불필요한 데이터 저장을 사전 차단했다. 기존 데이터와 diff를 비교하여 변경분만 upsert하고, soft-delete/hard-delete를 상황에 맞게 구분 적용하여 데이터 정합성을 보장했다. `RestaurantSyncService`에 배치 처리 로직을 351줄 이상 확장하여 동시성 제어, 에러 핸들링, 재시도 전략을 포함한 안정적인 동기화 파이프라인을 구축했다. 카테고리 결정 로직을 `RestaurantCategoryResolver`로 분리하여 Kakao API 응답의 카테고리 매핑 책임을 명확히 했다.

### 4. 멀티모듈 아키텍처 설계 및 Admin 모듈 독립 분리

모듈 간 의존성이 얽혀 Admin 기능을 API 모듈에 포함시켜야 했고, 외부 API 클라이언트가 불필요한 모듈에서도 로드되는 비효율이 있었다. `apps:api`, `apps:admin`, `apps:domain`, `batch:sync`, `external:ai/kakao`, `storage:db-core`, `support:logging/monitoring/swagger` 총 10개 모듈로 책임을 분리했다. Application 모듈에는 `java` 플러그인을, 라이브러리 모듈에는 `java-library` 플러그인을 적용하여 의존성 API 누수를 방지하고, `bootJar`는 실행 모듈에서만 활성화했다. 외부 API 클라이언트(Kakao/Gemini)에 `@ConditionalOnProperty` 조건부 활성화와 모듈별 타임아웃 설정을 추가하여, 각 모듈이 필요한 의존만 로드하도록 최적화했다.

### 5. Admin 백오피스 CRUD API 및 JWT 인증 체계 구현

운영팀이 맛집/모임 데이터를 직접 관리할 백오피스 도구가 없어 DB 직접 수정에 의존하는 상황이었다. 맛집 CRUD(목록 조회/상세/검색/생성/수정), 모임 상세 조회(참여자/추천 결과 포함), JWT Token Refresh API 등 Admin 전용 RESTful API를 설계·구현했다. Kakao API와 연동하는 맛집 검색/생성 플로우를 구축하고, `KakaoAdminKakaoApiExecutor`(261줄)로 Admin 전용 외부 API 실행기를 분리했다. QueryDSL을 활용하여 카테고리 조인 쿼리를 최적화하고 N+1 문제를 해결했으며, CORS 정책 설정과 에러 매핑까지 포함한 완결된 Admin API 레이어를 구축했다.

### 6. 추천 후보 쿼리 최적화 및 캐시 전략 적용

추천 시 지역 전체 맛집을 메모리로 로드한 후 Java에서 필터링하는 방식이 데이터 증가에 따라 성능 병목이 되고 있었고, Fallback 단계마다 점수 계산이 중복 실행되었다. `findRecommendationCandidates(region, categoryIds, timeSlot)` 전용 쿼리를 도입하여 SQL 레벨에서 카테고리/시간대/삭제 여부를 선필터링하고, 필요한 컬럼만 projection 조회하여 row 전송량을 줄였다. Fallback 단계별 점수 계산을 1회로 통합하고 전략별 필터만 분기하는 구조로 변경하여 계산 중복을 제거했다. `@Cacheable("categories")`로 카테고리 캐시를 적용하고, `ObjectProvider`로 CacheManager를 optional 처리하여 캐시 미지원 모듈에서도 안전하게 동작하도록 구현했다.

### 7. Docker Compose 멀티 환경 배포 자동화 및 운영 안정성 확보

Dev/Prod 환경별 리소스 할당이 코드에 하드코딩되어 있었고, 배포 시 컨테이너 상태 관리 미비와 네트워크 문제로 장애가 반복 발생했다. Docker Compose를 Dev/Prod/Edge 프로필로 분리하고, CPU/메모리 할당, JDBC 설정 등을 환경 변수화하여 IaC를 실현했다. 배포 스크립트에 이미지 pull 검증, DB 컨테이너 네트워크 연결 보장, stale 컨테이너 자동 정리 로직을 순차적으로 추가하며 배포 실패를 핫픽스 단위로 즉시 해결했다. JDBC URL 설정 유연화로 컨테이너/로컬 환경 모두에서 동일 설정으로 동작하게 하고, Dev 환경 리소스를 2vCore 4GB로 증대하여 동기화 배치 안정성을 확보했다.

### 8. 동시성 테스트 안정화 및 Hotfix 대응 경험

`ParticipantFacadeConcurrencyTest`에서 다수 스레드가 동시에 모임 참여를 시도할 때 간헐적으로 테스트가 실패하는 문제가 있었다. 스레드 타이밍과 DB 격리 수준 조합에서 발생하는 Race Condition을 분석하여 테스트 안정성을 확보했다. 운영 중 참여자 불호 수 제한(PARTICIPANT_DISLIKES_EXCEEDED) 검증 로직에서 경계값 오류를 발견하여 긴급 핫픽스를 배포하고, 동시에 테스트 코드를 보강하여 재발을 방지했다. 이 과정에서 문제 발견 → 핫픽스 배포 → 테스트 보강까지의 프로덕션 대응 프로세스를 체득했다.

---

## 핵심 어필 포인트 요약

| 역량 | 구체적 사례 |
|:--|:--|
| 알고리즘 설계 | 투표 비율 기반 카테고리 슬롯 배분(최대 나머지법), 8개 가중치 점수 모델, Strict + 2단계 Fallback |
| 설계/리팩토링 | 700줄+ 단일 클래스 → 전략 패턴 기반 4개 컴포넌트 분리, OCP 준수 |
| 아키텍처 | Gradle 10개 멀티모듈 설계, 조건부 빈 활성화, Admin 모듈 독립 분리 |
| 성능 최적화 | SQL 선필터링 쿼리, 점수 계산 1회 통합, 카테고리 캐시, QueryDSL 조인 최적화 |
| 외부 연동 | Kakao API 동기화 파이프라인, Haversine 지리 필터링, Gemini AI 요약 부스트 |
| 운영/배포 | Docker Compose 멀티 환경 IaC, 배포 자동화 스크립트, Hotfix 즉시 대응 |
| 품질 관리 | 12개 시나리오 테스트, 동시성 테스트 안정화, Spotless + Git Hook 자동화 |
