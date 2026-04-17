---
name: yogieat-develop
description: "yogieat-server 기능 개발 전체 워크플로우를 자동화하는 오케스트레이터. 새 기능, API 추가, 도메인 확장 요청 시 분석 → 구현 → QA 파이프라인을 실행한다. '기능 추가', '도메인 만들어', 'API 구현', '새로운 엔드포인트', 'CRUD 만들어', '~기능 개발해줘' 등의 요청에 반드시 이 스킬을 사용할 것."
---

# Yogieat Development Orchestrator

yogieat-server의 멀티모듈 클린 아키텍처에 맞게 기능을 개발하는 전체 워크플로우를 조율한다.

## 실행 모드: 서브 에이전트

파이프라인 패턴. 각 Phase의 산출물이 다음 Phase의 입력이 된다.

## 에이전트 구성

| 에이전트 | subagent_type | 역할 | 출력 |
|---------|--------------|------|------|
| architect | architect | 요구사항 분석 + 구현 계획 수립 | `_workspace/01_architect_plan.md` |
| implementor | implementor | 멀티모듈 코드 구현 | 코드 파일 생성/수정 |
| qa-inspector | qa-inspector | 통합 정합성 검증 + 빌드 | `_workspace/04_qa_report.md` |

## 워크플로우

### Phase 1: 준비

1. 사용자 입력에서 기능 요구사항을 파악한다
2. 프로젝트 루트에 `_workspace/` 디렉토리를 생성한다

### Phase 2: 설계

architect 에이전트를 호출하여 구현 계획을 수립한다.

```
Agent(
  description: "기능 설계",
  subagent_type: "architect",
  model: "opus",
  prompt: "다음 기능 요구사항에 대한 구현 계획을 수립하라.
    
    요구사항: {사용자 요구사항}
    
    프로젝트 루트: /Users/yunbeom/ybcha/nexters/yogieat-server
    
    1. 기존 코드베이스에서 유사 도메인 패턴을 탐색하라
    2. 영향받는 모듈과 생성/수정할 파일을 full package path로 명시하라
    3. 의존성 방향 위반이 없는지 확인하라
    4. 결과를 _workspace/01_architect_plan.md에 저장하라"
)
```

계획 산출물을 Read하여 합리성을 검토한다. 문제가 있으면 사용자에게 확인 후 수정.

### Phase 3: 구현

implementor 에이전트를 호출하여 코드를 구현한다.

```
Agent(
  description: "코드 구현",
  subagent_type: "implementor",
  model: "opus",
  prompt: "architect의 구현 계획에 따라 코드를 구현하라.
    
    프로젝트 루트: /Users/yunbeom/ybcha/nexters/yogieat-server
    구현 계획: _workspace/01_architect_plan.md를 Read하라
    구현 가이드: .claude/skills/spring-multimodule-implement/SKILL.md를 Read하라
    
    구현 순서를 반드시 따르라:
    domain model → repository interface → service → facade → entity → core repository → controller + DTO → tests
    
    구현 완료 후 ./gradlew spotlessApply를 실행하라"
)
```

### Phase 4: QA 검증

qa-inspector 에이전트를 호출하여 통합 정합성을 검증한다.

```
Agent(
  description: "QA 검증",
  subagent_type: "qa-inspector",
  model: "opus",
  prompt: "구현된 코드의 멀티모듈 통합 정합성을 검증하라.
    
    프로젝트 루트: /Users/yunbeom/ybcha/nexters/yogieat-server
    QA 체크리스트: .claude/skills/yogieat-qa/SKILL.md를 Read하라
    구현 계획: _workspace/01_architect_plan.md를 참조하라
    
    git diff로 변경된 파일을 확인하고 6가지 검증을 수행하라.
    결과를 _workspace/04_qa_report.md에 저장하라"
)
```

### Phase 5: 수정 루프 (최대 2회)

QA 리포트를 Read한다. FAIL 항목이 있으면:

1. 위반 사항을 요약한다
2. implementor를 재호출하여 수정 지시:
   ```
   Agent(
     description: "QA 위반 수정",
     subagent_type: "implementor",
     model: "opus",
     prompt: "QA 검증에서 다음 위반이 발견되었다. 수정하라.
       
       {QA 리포트의 FAIL 항목 전문}
       
       수정 완료 후 ./gradlew spotlessApply를 실행하라"
   )
   ```
3. qa-inspector를 재호출하여 재검증
4. 2회 반복 후에도 FAIL이면 남은 이슈를 사용자에게 보고

### Phase 6: 정리

1. `_workspace/` 디렉토리는 보존한다 (사후 검증용)
2. 사용자에게 결과 요약을 보고한다:
   - 생성/수정된 파일 목록
   - QA 검증 결과 (PASS/FAIL)
   - 남은 이슈 (있는 경우)

## 데이터 흐름

```
사용자 요구사항
    ↓
[architect] → _workspace/01_architect_plan.md
    ↓
[implementor] → 코드 파일 생성/수정
    ↓
[qa-inspector] → _workspace/04_qa_report.md
    ↓ (FAIL 시)
[implementor] → 수정 ←→ [qa-inspector] → 재검증 (최대 2회)
    ↓
결과 요약 → 사용자
```

## 에러 핸들링

| 상황 | 전략 |
|------|------|
| architect 실패 | 사용자에게 요구사항 명확화 요청 |
| implementor 컴파일 에러 | QA에서 에러 메시지 수집 → implementor 재호출 |
| QA 경계면 위반 | 구체적 위반 사항 → implementor 수정 (최대 2회) |
| 2회 수정 후에도 FAIL | 남은 이슈를 사용자에게 보고, 수동 해결 제안 |
| gradle 타임아웃 | 빌드 스킵, 경계면 검증만 수행 후 보고 |

## 테스트 시나리오

### 정상 흐름
1. 사용자: "review 도메인 추가해줘. 추천 결과에 리뷰를 남길 수 있는 기능"
2. architect: domain/storage/api 3개 모듈에 파일 목록 도출
3. implementor: Review Record → ReviewRepository → ReviewService → ReviewFacade → ReviewEntity → ReviewCoreRepository → ReviewController + DTOs 생성
4. qa-inspector: 6가지 검증 PASS
5. 결과 요약 보고

### 에러 흐름
1. implementor가 domain에 `@Entity` import 추가
2. qa-inspector가 Domain 순수성 위반 탐지
3. implementor 수정 → 재검증 PASS
