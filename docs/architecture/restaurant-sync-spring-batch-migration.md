# Restaurant Sync — Spring Batch 점진 전환 가이드

> 최종 업데이트: 2026-03-09

## 왜 이 전환 가이드를 미리 작성했는가

현재 `batch:sync`는 커스텀 워커 기반으로 동작하며, 현 요구사항에서는 충분합니다.
그러나 배치 복잡도 증가 시 **안전하게 Spring Batch로 전환할 수 있는 경로**를 미리 설계해 두었습니다.

| 판단 기준 | 현재 상태 | 전환 시 이점 |
|:--|:--|:--|
| 실패 복구 | 수동 재실행 | 자동 재시작 + 체크포인트 |
| 에러 처리 | 단순 재시도 | 오류 코드별 skip/retry 정책 |
| 확장성 | 단일 Job | 다수 Job/Step 관리 |
| 운영 가시성 | 자체 테이블 | Spring Batch 메타데이터 + 대시보드 |

---

## 현재 구현 특성

- `t_restaurant_sync_job` 기반 Job 상태 영속화
- `ALL`/`SINGLE` 두 가지 실행 스코프
- 청크 처리 (50건) + 병렬 처리 (4 스레드) + 청크 레벨 재시도 (최대 2회, 지수 백오프)
- Graceful shutdown (awaitTermination 30초)
- 주간 스케줄 + 수동 API 트리거

---

## 도입 판단 기준

아래 조건 중 **2개 이상 충족** 시 Spring Batch 전환을 권장합니다.

1. 처리 데이터 급증으로 실패 복구 및 재시작 요구가 커짐
2. 외부 API 실패 정책이 복잡해져 오류 코드별 skip/retry 제어가 필요함
3. 동기화 외 배치 Job 종류가 다수 추가됨
4. 운영 관점에서 표준 실행 메타데이터 및 대시보드 요구가 강화됨
5. 다중 노드 분산 실행 요구가 발생함

---

## 전환 원칙

### 1. API/도메인 계약 유지
- `/api/v1/restaurants/sync-jobs/*` 계약 유지
- `RestaurantSyncJobService`는 Job 생성/조회 책임 유지

### 2. 실행 엔진만 교체
- `sync.engine=custom|spring-batch` 플래그로 실행 경로 분기

### 3. 사용자 상태 소스 유지
- 사용자/운영 API는 계속 `t_restaurant_sync_job`를 조회
- Spring Batch 메타테이블은 내부 실행 추적 용도

### 4. 롤백 가능성 확보
- 플래그를 `custom`으로 되돌리면 기존 워커 즉시 재사용

---

## 타겟 아키텍처

### 구성

- **API/도메인**: `RestaurantSyncJobService` — Job row 생성/조회
- **실행 엔진**:
  - custom: `RestaurantSyncJobWorker`
  - spring-batch: `JobLauncher` + `Job/Step`
- **영속성**:
  - 비즈니스 상태: `t_restaurant_sync_job`
  - 배치 메타: Spring Batch 기본 테이블

### 매핑 테이블 (권장)

Spring Batch execution과 비즈니스 Job 매핑을 위해 아래 테이블 추가를 권장합니다.

- 테이블: `t_restaurant_sync_job_execution_map`
- 컬럼:
  - `restaurant_sync_job_id` (FK)
  - `batch_job_execution_id`
  - `created_at`

---

## Job/Step 설계

### Job

- 이름: `restaurantSyncJob`
- 파라미터: `restaurantSyncJobId` (필수), `scope` (`ALL`/`SINGLE`)

### Step 설계

**`scope=SINGLE`** — Tasklet step
- 대상 ID 1건 `RestaurantSyncService.syncOne(id)` 실행

**`scope=ALL`** — Chunk step
- Reader: keyset 기반 ID 조회
- Processor: `RestaurantSyncService.syncOne(id)`
- Writer: 성공/실패 집계 및 `t_restaurant_sync_job` 진행률 업데이트

### Listener

- `beforeJob`: 상태 `RUNNING`, `startedAt`, `totalCount` 초기화
- `afterJob`:
  - 성공 전건: `SUCCESS`
  - 부분 실패: `PARTIAL_FAILED`
  - 시스템 실패: `FAILED`
  - `finishedAt`, `errorSummary` 업데이트

---

## 장애 처리 정책

### 외부 API 호출 실패
- Retry: 2~3회 (짧은 backoff)
- Retry 초과 시 skip 후 failed 카운트 증가

### 재시작
- 재실행 시 `last_processed_restaurant_id` 기준으로 이어서 처리
- Spring Batch `JobExecution`은 신규 생성 가능
- 비즈니스 상태 일관성은 `t_restaurant_sync_job` 기준으로 유지

---

## 성능/동시성 정책

- 병렬도: 4 (기존값 유지)
- 청크 크기: 50 (기존값 유지)
- 동시 실행 제약:
  - ALL: RUNNING/PENDING 중복 생성 금지
  - SINGLE: 동일 restaurantId 중복 생성 금지

---

## 단계별 마이그레이션

### Phase 0. 준비

1. `batch:sync`에 Spring Batch 의존성 추가
2. Spring Batch 메타테이블 준비
3. 실행 엔진 플래그 추가 (`sync.engine`)

완료 기준: 기존 custom 경로 동작 변화 없음

### Phase 1. 병행 구성

1. `BatchConfig` 추가 (`restaurantSyncJob`, `singleStep`, `allStep`)
2. `BatchLauncherService` 추가 (`restaurantSyncJobId`를 Job parameter로 전달)
3. 매핑 테이블 (`t_restaurant_sync_job_execution_map`) 반영

완료 기준: 로컬에서 Spring Batch job 단독 실행 가능

### Phase 2. 트리거 연결

1. 스케줄러/수동 트리거에서 `sync.engine=spring-batch`일 때 launcher 호출
2. `custom` 워커는 유지하되 조건부 비활성화

완료 기준: API/스케줄 호출 시 Spring Batch 경로 정상 실행, 상태 집계가 `t_restaurant_sync_job`에 정상 반영

### Phase 3. 안정화

1. 실패/재시작/부분실패 시나리오 통합 테스트
2. 처리시간/실패율 비교
3. 운영 모니터링 지표 연동

완료 기준: 커스텀 대비 처리시간/실패율 악화 없음

### Phase 4. 전환 완료

1. 운영 기본값을 `sync.engine=spring-batch`로 전환
2. 일정 기간 관찰 후 custom 워커 제거 여부 결정

완료 기준: 롤백 없이 안정 운영

---

## 테스트 시나리오

1. SINGLE 성공/실패/재시도
2. ALL 청크 순회 정확성 (keyset 경계)
3. PARTIAL_FAILED 집계 정확성
4. 동일 Job 중복 방지 검증
5. 엔진 플래그 전환 (`custom` ↔ `spring-batch`) 검증

---

## 운영 가이드

### 권장 설정

- `sync.engine=custom` (초기)
- `sync.engine=spring-batch` (전환 단계)
- `sync.job.chunk-size=50`
- `sync.job.parallelism=4`

### 롤백 절차

1. 설정을 `sync.engine=custom`으로 변경
2. 배치 프로세스 재기동
3. `t_restaurant_sync_job` 상태 확인

---

## 트레이드오프 요약

| 관점 | Spring Batch 장점 | Spring Batch 단점 |
|:--|:--|:--|
| 안정성 | 표준 재시작/체크포인트 | 초기 구성 복잡도 증가 |
| 운영 | skip/retry/listener 풍부 | 학습/운영 비용 증가 |
| 확장성 | Job/Step 단위 확장 우수 | 외부 API 중심 워크로드에서 성능 이득 제한적 |

---

## 결론

현 시점은 **커스텀 워커 유지가 합리적**이며, 본 가이드 기반으로 엔진 플래그를 도입해 점진 전환 가능성을 열어두는 전략을 기본으로 합니다.
