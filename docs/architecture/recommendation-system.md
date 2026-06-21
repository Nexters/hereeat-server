# 맛집 추천 시스템 아키텍처

> 최종 업데이트: 2026-05-08

## 왜 직접 추천 알고리즘을 설계했는가

Yogieat의 추천 시스템은 외부 ML 서비스 없이 **순수 Java 로직으로 구현한 다요소 점수 기반 추천 엔진**입니다.

| 판단 기준 | 선택 | 이유 |
|:--|:--|:--|
| 추천 방식 | 규칙 기반 점수 모델 | 소규모 서비스에서 ML 모델 학습 데이터가 부족하고, 투표 기반 입력은 규칙이 명확함 |
| 다양성 보장 | 카테고리 쿼터 슬롯 배분 | 단순 점수 정렬 시 한 카테고리에 편중되는 문제를 투표 비율 기반 슬롯으로 해결 |
| 가용성 보장 | 다단계 Fallback | 엄격한 필터링으로 Top 3를 못 채우는 상황을 방지 |
| AI 활용 | Gemini 요약 → 점수 부스트 | AI 요약 키워드를 점수에 반영하여 단체 모임, 인기 맛집 등 맥락을 반영 |

---

## 개요

추천 파이프라인의 계산 진입점은 `RecommendationProcessor.calculateRecommendations()`이고, 최초 추천 저장 진입점은 `RecommendationProcessor.processRecommendation()`입니다.
모임 참여자의 선호/불호/거리 선호를 집계해 **대표 Top 3 + 추가 추천을 합쳐 최대 `resultSize`(기본 9)개 맛집을 자동 산출**하고, 최초 추천 결과는 `RecommendResult`에 rank 1~N으로 저장합니다. 따라서 결과 조회 API(`GET /api/v1`, `GET /api/v2`)는 저장된 결과를 그대로 읽는 단순 조회로 동작합니다.
재추천은 기존 결과를 덮어쓰지 않고 제외할 맛집 목록을 반영해 후보를 다시 산출한 뒤 `RecommendRerollHistory`에 요청/결과 이력을 저장합니다.

핵심 목표:

1. **선호 투표 비율을 반영한 카테고리 안배 추천** — 일식 3표, 아시안 2표이면 슬롯 2:1 배분
2. **불호 우세 카테고리 제거** — 불호가 선호보다 많으면 SQL 단계에서 사전 제외
3. **Top 3 보장과 선호 우선 정책의 균형** — Strict 필터 + Fallback 전략
4. **모임 일정 맥락 반영** — 시간대와 휴무일을 후보 쿼리에서 선필터링

---

## 핵심 컴포넌트

```mermaid
graph TB
    subgraph "Orchestration"
        RRF["RecommendResultFacade<br/>API 유스케이스 + 락 + 이벤트"]
        RP["RecommendationProcessor<br/>점수 계산 + fallback 제어"]
    end

    subgraph "Context"
        RCF["RecommendationContextFactory<br/>참여자 입력 → 추천 컨텍스트 집계"]
        RSP["RecommendationScoringPolicy<br/>가중치/임계값 정책 (Record)"]
    end

    subgraph "Selection"
        RSS["RecommendationSelectionStrategy<br/>Top-K 선정 인터페이스"]
        CQS["CategoryQuotaSelectionStrategy<br/>투표 비율 기반 슬롯 배분"]
    end

    subgraph "Persistence"
        RR["RecommendResult<br/>최초 추천 결과"]
        RRH["RecommendRerollHistory<br/>재추천 이력"]
        RRFail["RecommendResultFailed<br/>실패 컨텍스트"]
    end

    subgraph "Value Objects"
        PS["PreferenceScore<br/>카테고리별 선호 점수"]
        CVS["CategoryVoteSummary<br/>선호표/불호표/제외 카테고리"]
        DSC["DistanceScoreContext<br/>거리 다수결 + ANY 가중치"]
    end

    RRF --> RP
    RP --> RCF
    RP --> RSP
    RP --> RSS
    RRF --> RRH
    RP --> RR
    RP --> RRFail
    RSS -.->|"구현"| CQS
    RCF --> PS
    RCF --> CVS
    RCF --> DSC

    style RRF fill:#e1f5ff,stroke:#0288d1
    style RP fill:#e1f5ff,stroke:#0288d1
    style RCF fill:#fff4e1,stroke:#ff9800
    style RSP fill:#fff4e1,stroke:#ff9800
    style RSS fill:#ffe1e1,stroke:#e53935
    style CQS fill:#ffe1e1,stroke:#e53935
    style PS fill:#e1ffe1,stroke:#43a047
    style CVS fill:#e1ffe1,stroke:#43a047
    style DSC fill:#e1ffe1,stroke:#43a047
```

| 컴포넌트 | 역할 | 설계 패턴 |
|:--|:--|:--|
| `RecommendResultFacade` | 추천 결과 조회, 추천 진행, 재추천 API 유스케이스 오케스트레이션 | Facade |
| `RecommendationProcessor` | 추천 처리 오케스트레이션, 점수 계산, fallback 제어 | Orchestrator |
| `RecommendationContextFactory` | 참여자 입력을 추천용 파생 컨텍스트로 집계 | Factory |
| `RecommendationSelectionStrategy` | Top-K 선정 전략 인터페이스 | Strategy (인터페이스) |
| `CategoryQuotaSelectionStrategy` | 카테고리 투표 비율 기반 슬롯 배분 (최대 나머지 방식) | Strategy (구현) |
| `RecommendationScoringPolicy` | 가중치/임계값 정책 객체 | Record (불변 정책) |
| `RecommendValidator` | 과반수, 중복 진행, 재추천 가능 상태 검증 | Validator |
| `RecommendRerollHistory` | 재추천 요청에서 제외한 맛집과 산출 결과 스냅샷 저장 | Domain Record |
| `PreferenceScore` | 카테고리별 선호 점수 + 선호자/불호자 카운트 | Value Object |
| `CategoryVoteSummary` | 카테고리별 선호표/불호표/제외 카테고리 | Value Object |
| `DistanceScoreContext` | 거리 다수결 결과 + ANY 비중 반영 가중치 | Value Object |

---

## 처리 흐름

### 최초 추천 진행

```mermaid
flowchart TD
    A["POST /api/v1/recommend-results/proceed"] --> B["RecommendResultFacade.proceedRecommendation"]
    B --> C["accessKey 락 획득"]
    C --> D["모임 조회 + 현재 참여자 수 조회"]
    D --> E{"과반수 초과?<br/>currentCount * 2 > peopleCount"}
    E -->|No| F["PARTICIPANT_MAJORITY_NOT_REACHED"]
    E -->|Yes| G{"이미 추천 결과 존재?"}
    G -->|Yes| H["RECOMMEND_ALREADY_PROCEEDED"]
    G -->|No| I["PENDING 생성"]
    I --> J["RecommendResultCreatedEvent 발행"]
    J --> K["RecommendationEventListener"]
    K --> L["processRecommendation(gatheringId, region)"]

    style F fill:#ffe1e1
    style H fill:#ffe1e1
    style I fill:#fff4e1
    style L fill:#e1ffe1
```

### 추천 계산

```mermaid
flowchart TD
    A["calculateRecommendations 시작"] --> B["Gathering 조회<br/>(TimeSlot, scheduledDate)"]
    B --> C["Participant 조회"]
    C --> D{"참여자 존재?"}
    D -->|No| E["FAILED: NO_PARTICIPANTS"]
    D -->|Yes| F["RecommendationContextFactory.create()"]
    F --> G["Category 조회 (캐시)"]
    G --> H["불호 우세 카테고리 제외 후<br/>candidateCategoryIds 구성"]
    H --> I["findRecommendationCandidates<br/>(region, categoryIds, timeSlot,<br/>excludedRestaurantIds, scheduledDate)"]
    I --> J{"후보 존재?"}
    J -->|No| K["FAILED: NO_RESTAURANTS"]
    J -->|Yes| L["후보 점수 계산 1회<br/>(scoreRestaurants)"]
    L --> M["1단계: PREFERENCE_SCORE_POSITIVE"]
    M --> N["Strict 후보 필터<br/>(불호 0표 선호 카테고리)<br/>+ 슬롯용 유효표 계산"]
    N --> O{"Top K 충족?"}
    O -->|Yes| P["Top K 반환"]
    O -->|No| Q["2단계: DISLIKED_EXCLUDED"]
    Q --> R["merge(primary, fallback)"]
    R --> P

    style E fill:#ffe1e1
    style K fill:#ffe1e1
    style P fill:#e1ffe1
```

`processRecommendation()`은 위 계산을 두 단계로 수행합니다.

1. **대표 추천(1차)**: `topK=3`으로 튜닝된 Top 3를 산출합니다. 1차가 실패하면 PENDING을 정리하고 `FAILED`로 저장합니다.
2. **추가 추천(2차)**: 부족분(`resultSize - 3`, 기본 6)을 1차 결과를 `excludedRestaurantIds`로 제외하고 추가 계산해 합칩니다. 2차가 실패하면 1차 결과만 사용합니다.

성공 시 합쳐진 결과를 `COMPLETED` 상태의 `RecommendResult`로 rank 1~N(최대 `resultSize`, 기본 9)으로 한 번에 저장합니다. 이렇게 생성 시점에 N개를 미리 적재하므로 조회 시점에는 별도 계산이 없습니다.
예외가 발생하면 PENDING 레코드를 정리한 뒤 `FAILED` 상태와 `RecommendResultFailed` 상세 컨텍스트를 저장합니다.

### 재추천

```mermaid
flowchart TD
    A["POST /api/v1/recommend-results/reroll"] --> B["RecommendResultFacade.rerollRecommendResults"]
    B --> C["Gathering FOR UPDATE 조회"]
    C --> D["기존 추천 결과 조회"]
    D --> E{"COMPLETED 상태?"}
    E -->|No| F["NOT_FOUND 또는 REROLL_NOT_AVAILABLE"]
    E -->|Yes| G["요청 restaurantIds 정제<br/>(null/0 이하 제거, distinct)"]
    G --> H["calculateRecommendations<br/>(excludedRestaurantIds 포함)"]
    H --> I["재추천 응답 Ranking 생성"]
    I --> J["RecommendRerollHistory 저장"]

    style F fill:#ffe1e1
    style J fill:#e1ffe1
```

재추천은 현재 저장된 `RecommendResult`를 삭제하거나 교체하지 않습니다.
클라이언트가 넘긴 제외 맛집을 후보 쿼리의 `id NOT IN` 조건에 반영하고, 재추천 결과가 비어도 요청 이력은 저장합니다.

---

## 점수 모델

최종 점수는 9개 요소의 합이며, 소수점 셋째 자리에서 반올림합니다.

```text
totalScore
 = 선호도 점수
 + 순수 선호수 가산점
 + 신뢰도 점수
 + 거리 가산점 (ANY 비중 반영)
 + 의견일치율 가산점
 + AI 요약 부스트
 + Cold Start 부스트
 + Freshness 부스트
 + 팀 추천 부스트
```

### 1) 선호도 점수 — 순위별 가중 투표

참여자가 선택한 카테고리 선호 순위에 따라 가중치를 부여합니다.

| 항목 | 값 |
|:--|:--|
| 1순위 선호 | +3.0 |
| 2순위 선호 | +2.0 |
| 3순위 선호 | +1.0 |
| 불호 | -2.0 (개수당) |

```text
preferenceScore = totalPreferenceScore - (dislikeCount * 2.0)
```

> **설계 의도**: 단순 투표수가 아닌 순위별 가중치를 적용하여, "1순위로 선택한 1표"가 "3순위 1표"보다 3배 영향력을 갖도록 했습니다.

### 2) 순수 선호수 가산점 — 찬반 균형 보정

```text
netPreference = preferenceCount - dislikeCount
if netPreference > 0  → +1.0 × netPreference
if netPreference = 0  → -0.5
if netPreference < 0  → +2.0 × netPreference (음수 페널티)
```

### 3) 신뢰도 점수 — 리뷰 수 + 평점의 교차 검증

리뷰 수가 많고 평점이 높을수록 신뢰도가 올라갑니다. 로그 스케일을 적용하여 리뷰 1000개와 100개의 차이가 과도하게 벌어지지 않도록 합니다.

```text
kakaoWeight  = log10(reviewCount + 1)
blogWeight   = log10(blogReviewCount + 1) × 1.5
totalWeight  = min(kakaoWeight + blogWeight, 5.0)
normalizedRating = clamp((rating - 3.0) / (5.0 - 3.0), 0.0, 1.0)
credibility  = normalizedRating × totalWeight
```

> **설계 의도**: 블로그 리뷰는 일반 리뷰보다 상세하므로 1.5배 가중치를 적용했습니다. `log10` 스케일로 리뷰 수의 급격한 증가를 방지합니다.

### 4) 거리 가산점 — 다수결 + ANY 비중 반영

거리 다수결은 `RANGE_500M` vs `RANGE_1KM`로 결정하며, 동률 시 `RANGE_500M` 우선입니다.
`ANY` 선택 비중이 높을수록 거리 보너스를 선형으로 축소합니다.

```text
anyRatio = anyCount / participantCount
effectiveDistanceBonus = distanceBonus × (1 - anyRatio)
```

| 예시 | effectiveDistanceBonus |
|:--|:--|
| ANY 0% | 1.0 |
| ANY 50% | 0.5 |
| ANY 100% | 0.0 |

> **설계 의도**: "거리 상관없음" 선택이 많을수록 거리 요인의 영향력을 자연스럽게 줄여, 참여자 의사를 정확히 반영합니다.

### 5) 의견일치율 가산점

```text
agreementRate = (해당 카테고리 선호자 수 / 총 참여자 수) × 100
agreementBonus = agreementRate / 100
```

### 6) AI 요약 부스트 — Gemini 키워드 매칭

| 조건 | 점수 |
|:--|:--|
| 참여자 수 >= 4 + 단체 키워드(단체석/대형 테이블/모임/단체) | +0.5 |
| 긍정 키워드(추천/인기/맛집/특별/유명) | +0.3 |
| 부정 키워드(웨이팅 필수/예약 필수/대기 시간) | -0.2 |

### 7) Cold Start + Freshness

| 항목 | 규칙 |
|:--|:--|
| Cold Start | 등록 30일 이내 또는 리뷰 10개 미만이며, 평점 4.0 이상이면 +0.3 |
| Freshness | 7일 이내 +0.3, 30일 이내 +0.1, 90일 이상 -0.2 |

> **설계 의도**: Cold Start 부스트로 신규 맛집도 추천 대상에 포함되도록 하되, 평점 4.0 이상 조건으로 품질을 보장합니다. Freshness로 정보가 오래된 맛집에는 페널티를 부여합니다.

### 8) 팀 추천 부스트

| 조건 | 점수 |
|:--|:--|
| `teamRecommendationTitle`과 `teamRecommendationReason`이 모두 비어 있지 않음 | +3.0 |

> **설계 의도**: 운영팀이 제목과 사유를 모두 작성한 맛집은 사용자 1순위 카테고리 선호 1표와 동일한 강도의 큐레이션 신호로 반영합니다.

참고: `RecommendationScoringPolicy`에 `diversityBonus` 파라미터가 정의되어 있으나, 현재 점수 합산에는 사용하지 않습니다.

### 설정 오버라이드

`RecommendationScoringPolicy`는 `@ConfigurationProperties(prefix = "recommendation.scoring")`로 바인딩되어, `application.yaml`에서 모든 파라미터를 오버라이드할 수 있습니다. AI 요약 키워드(그룹/긍정/부정)도 설정으로 관리됩니다.

```yaml
recommendation:
  scoring:
    distance-bonus: 1.0
    team-recommendation-boost: 3.0
    candidate:
      pool-size: 10      # 카테고리당 후보 풀 크기
      top-k-size: 3      # 1차 대표 추천/재추천 1회당 선정 수
      result-size: 9     # 최초 추천 생성 시 적재할 총 결과 수 (1차 + 추가)
    ai-summary:
      group-size-threshold: 4
      group-keywords: ["단체석", "대형 테이블", "모임", "단체"]
      positive-keywords: ["추천", "인기", "맛집", "특별", "유명"]
      negative-keywords: ["웨이팅 필수", "예약 필수", "대기 시간"]
```

---

## 카테고리 필터링 정책

`RecommendationContextFactory`가 카테고리별 선호표/불호표를 집계하고, 아래 규칙으로 추천 제외 카테고리를 결정합니다.

1. `dislikeCount > preferenceCount` 인 카테고리 제외
2. `preferenceCount = 0 && dislikeCount > 0` 인 카테고리 제외

이 제외 카테고리는 추천 후보 조회 전에 **SQL 조건으로 사전 반영**합니다. 불필요한 데이터를 메모리에 올리지 않습니다.

입력 매핑 규칙:

1. `LargeCategory.displayName` 기준 입력(예: `아시안`)을 처리
2. enum name 입력(예: `ASIAN`)도 처리
3. 그 외 별칭 문자열은 현재 별도 정규화하지 않음

---

## Top-K 선정 알고리즘

`RecommendationProcessor` + `CategoryQuotaSelectionStrategy` 조합으로 Top-K를 결정합니다.

### 1) Strict 후보 우선 규칙 (`applyNoDislikePreferredFilter`)

선호 카테고리 중 `dislikeVotes == 0`인 카테고리가 있으면 strict 후보로 우선 사용합니다.

1. strict 카테고리가 없으면 전체 후보 유지
2. strict 후보가 비어있으면 전체 후보 유지
3. strict 후보 수가 `topK` 미만이면 Top 3 보장을 위해 전체 후보로 복귀
4. strict 후보 수가 `topK` 이상이면 strict 후보만 사용

> **설계 의도**: "아무도 싫어하지 않는 카테고리"를 우선 추천하여, 모임에서 불만이 최소화되는 결과를 도출합니다.

### 2) 단계별 포함 조건 (`FilterStrategy`)

Strict 후보 적용 후에도 단계별 포함 조건을 한 번 더 적용합니다.

| 단계 | 조건 |
|:--|:--|
| `PREFERENCE_SCORE_POSITIVE` | `totalPreferenceScore > 0` 이고 `netPreference >= -1` |
| `DISLIKED_EXCLUDED` | `netPreference >= -1` |

`netPreference = preferenceCount - dislikeCount` 이므로, 현재 구현은 불호가 선호보다 2명 이상 많은 카테고리를 단계 필터에서 제외합니다.
불호가 선호보다 많은 카테고리는 앞선 카테고리 제외 정책에서 이미 후보 카테고리에서 빠지지만, 단계 필터는 strict 후보 복귀나 중립 입력 케이스에서도 동일한 안전장치로 동작합니다.

### 3) 슬롯 배분용 유효표 산정 (`buildQuotaPreferenceVotes`)

카테고리 슬롯 비율은 아래 우선순위로 투표값을 사용합니다.

1. **가중 선호 유효표**: `round(totalPreferenceScore) - dislikeVotes × 2` (0 미만은 0으로 절삭)
2. **단순 선호표**: 위가 전부 0이면 `preferenceVotes` 사용
3. **균등표**: 선호 입력 자체가 없으면 후보 카테고리당 1표

### 4) `CategoryQuotaSelectionStrategy` 슬롯 배분

1. 카테고리별 후보를 점수순 정렬하고, 카테고리당 최대 `candidatePoolSize(10)`개 유지
2. `rawQuota = (categoryVotes / totalVotes) × topK(3)` 계산
3. 바닥값 슬롯 우선 할당
4. 남은 슬롯은 **최대 나머지 방식(Largest Remainder Method)**으로 배분
5. 슬롯으로 부족하면: 먼저 quota 카테고리 내부에서 점수순 보강, 그래도 부족하면 전체 카테고리에서 점수순 보강하여 Top K를 채움

> **예시**: `일식 3표, 아시안 2표`일 때 Top 3 슬롯은 `2:1`로 배분됩니다.

---

## 추천 근거 텍스트

추천 결과에는 사용자에게 보여줄 **추천 근거 텍스트**(`reasonText`)가 함께 저장됩니다.

형식 예시:
```text
5명 중 3명이 일식을 골라서
400시간 숙성으로 완성한 겉바속촉 돈카츠
을(를) 추천해요
```

구성 요소:
1. **참여자 선호 정보** — "N명 중 M명이 {카테고리}를 골라서"
2. **AI 요약 타이틀** — Gemini가 생성한 맛집 한줄 요약
3. **마무리 멘트** — "을(를) 추천해요"

---

## 쿼리/메모리 최적화

### 1) 추천 후보 전용 쿼리

기존은 `findByRegion()`으로 지역 전체 맛집을 메모리로 가져온 뒤 Java에서 필터링했습니다.
현재는 `findRecommendationCandidates(region, categoryIds, timeSlot, excludedRestaurantIds, scheduledDate)`로 SQL 선필터링을 수행합니다.

적용 조건:

1. `deleted_at IS NULL`
2. `region = ?`
3. `category_id IN (?)`
4. `time_slot IS NULL OR time_slot = BOTH OR time_slot = gatheringTimeSlot`
5. 재추천 제외 맛집이 있으면 `id NOT IN (:excludedRestaurantIds)`
6. 모임 예정일이 있으면 `off_days` JSON 텍스트에 해당 날짜가 포함되지 않음

`gatheringTimeSlot`이 `null` 또는 `BOTH`이면 시간대 조건은 생략합니다.
`scheduledDate`가 없으면 휴무일 조건도 생략합니다.

또한 추천 계산에 필요한 컬럼만 projection 조회해 row 폭을 줄였습니다.
추천 후보 projection은 점수 계산에 필요한 `id`, `categoryId`, `name`, `rating`, `regionId`, `location`, 리뷰 수, AI 요약, `timeSlot`, 생성/수정일, 휴무일 중심으로 구성합니다.

### 2) Fallback 점수 계산 중복 제거

기존에는 1단계/2단계 fallback 각각에서 점수 계산을 반복했습니다.
현재는 `scoreRestaurants()`로 **점수 계산 1회** 후, 전략별 필터만 분기합니다.

### 3) Category 캐시

`CategoryService.findAll()`에 `@Cacheable("categories")`를 적용하고, 카테고리 생성 시 캐시를 비웁니다.
`CacheManager`는 `ObjectProvider`로 optional 처리해 컨텍스트별 빈 유무 차이에도 안전합니다.

---

## 시간/공간 복잡도

| 기호 | 의미 |
|:--|:--|
| `P` | 참여자 수 |
| `M` | 카테고리 수 |
| `R` | 쿼리 후 추천 후보 맛집 수 |
| `S` | 투표에 등장한 카테고리 수 (`S <= M`) |
| `K` | 최종 추천 개수 (`K = 3`) |

### 현재 복잡도

| 단계 | 시간 복잡도 | 공간 복잡도 |
|:--|:--|:--|
| 참여자 컨텍스트 집계 | `O(P)` | `O(M)` |
| 카테고리 맵/후보 카테고리 구성 | `O(M)` | `O(M)` |
| 후보 점수 계산 1회 | `O(R)` | `O(R)` |
| 카테고리 버킷 정렬/슬롯 배분 | 최악 `O(R log R) + O(S log S)` | `O(R + S)` |
| fallback 병합 | `O(K)` | `O(K)` |

총합(지배항):

```text
Time:  O(P + M + R log R)
Space: O(M + R)
```

`K=3`, `candidatePoolSize=10`이 고정이므로 실제 런타임은 `R`과 카테고리 분포에 가장 민감합니다.

### 최적화 전/후 비교

| 항목 | 이전 | 현재 |
|:--|:--|:--|
| DB 조회 범위 | 지역 전체 | 지역+카테고리+TimeSlot+휴무일+제외 맛집 선필터링 |
| fallback 점수 계산 | 최대 2회 | 1회 |
| 카테고리 조회 | 매번 DB hit | 캐시 기반 |
| 선호도 집계 | 레스토랑 루프 내부 반복 위험 | 사전 집계 컨텍스트 재사용 |

---

## 사용 기술

| 구분 | 기술 |
|:--|:--|
| 언어/모델 | Java Record 기반 Value Object |
| 프레임워크 | Spring Boot, Spring Transactional, Spring Cache |
| 데이터 접근 | Spring Data JPA, QueryDSL |
| 지리 계산 | GeoJson + Haversine (`GeoUtils`) |
| 패턴 | Strategy (`RecommendationSelectionStrategy`), Factory (`RecommendationContextFactory`), Orchestrator (`RecommendationProcessor`) |
| 테스트 | JUnit 5, Mockito |

---

## 실패 처리

| FailureReason | 의미 |
|:--|:--|
| `NO_PARTICIPANTS` | 참여자 없음 |
| `NO_RESTAURANTS` | 후보가 없거나 필터링 후 추천 불가 |
| `PROCESSING_EXCEPTION` | 처리 중 예외 |
| `ASYNC_PROCESSING_TIMEOUT` | 비동기 처리 타임아웃 또는 오래된 PENDING 정리 |

실패 시:

1. `t_recommend_result`에 `FAILED` 상태 저장
2. `t_recommend_result_failed`에 상세 실패 컨텍스트 저장 (실패 사유, 에러 메시지, 발생 시각)

`PendingRecommendCleanupJob`은 10분마다 실행되며, 1분 이상 `PENDING` 상태로 남은 추천 결과를 찾습니다.
각 PENDING 레코드는 `PendingRecordCleanupProcessor`에서 독립 트랜잭션으로 삭제 후 `FAILED` 결과와 `ASYNC_PROCESSING_TIMEOUT` 실패 컨텍스트로 전환합니다.

---

## 테스트 커버리지

핵심 시나리오는 아래 테스트로 검증합니다.

**`RecommendationContextFactoryScenarioTest`**
- Case 1~12: 다양한 참여자 입력 조합에서 제외 카테고리 계산 검증

**`RecommendationProcessorTest`**
- 선호표 비율 슬롯 배분 (3:2 → 2:1)
- Case 11: 한식 4표/양식 2표 → 2:1 배분
- Strict 후보 우선 (불호 0표 선호 카테고리) 및 strict 후보 부족 시 Top 3 보강
- 가중 선호 유효표 기반 동률 우선순위
- 선호 중립 입력 ("상관없음")에서도 Top 3 반환
- ANY 비중에 따른 거리 보너스 선형 축소
- 후보 조회 시 category/timeSlot/excludedRestaurantIds/scheduledDate 선필터 파라미터 전달
- 재추천 제외 맛집 반영 후 결과 산출

**`CategoryQuotaSelectionStrategyTest`**
- 동률 카테고리 슬롯 분산
- 선호 후보 부족 시 비선호 카테고리 보강으로 Top K 충족

**`RecommendResultFacadeTest`**
- 과반수 충족 시 PENDING 생성 및 이벤트 발행
- 재추천 가능 상태 검증, 제외 맛집 정제, 재추천 이력 저장

**`PendingRecordCleanupProcessorTest`**
- 오래된 PENDING 레코드의 FAILED 전환 및 실패 컨텍스트 저장
