---
name: yogieat-qa
description: "yogieat-server 멀티모듈 통합 정합성 QA 체크리스트. 코드 구현 후 모듈 간 경계면을 교차 비교하여 의존성 위반, Entity-Record 매핑 누락, Repository 구현 누락, @Transactional 경계 오류를 탐지한다. 코드 리뷰, QA, 검증, 빌드 확인 요청 시 사용."
---

# Yogieat QA — 멀티모듈 통합 정합성 검증

코드 구현 후 모듈 간 경계면을 교차 비교하여 아키텍처 위반과 통합 결함을 탐지하는 체크리스트.

핵심 원칙: **"존재 확인"이 아닌 "양쪽 동시 읽기"로 경계면을 교차 비교**한다.

## 검증 순서

P0(치명적) → P1(중요) → 빌드/테스트 순서로 수행한다.

---

## [P0] 1. Domain 순수성 검증

`apps/domain/src` 내에서 다음 패턴이 0건이어야 통과한다:

```
검색 대상 디렉토리: apps/domain/src/main/java
검색 패턴:
  - jakarta.persistence
  - javax.persistence
  - com.querydsl
  - com.yogieat.datasource
  - org.springframework.data
  - org.hibernate
```

**방법**: Grep으로 각 패턴을 `apps/domain/src/main/java` 디렉토리에서 검색한다.

**위반 시 리포트**:
```
[P0] Domain 순수성 위반
- 파일: {파일경로}:{라인번호}
- 위반: 금지 import "{import문}"
- 수정: 해당 import 제거. domain 모듈에서는 순수 Java 타입과 도메인 모델만 사용
```

---

## [P0] 2. Record-Entity 매핑 검증

**양쪽 동시 읽기**: domain Record와 대응 Entity를 함께 열어 비교한다.

검증 항목:
1. 새 domain Record(`apps/domain/.../domain/{Domain}.java`)마다 대응 Entity(`storage/db-core/.../core/{domain}/{Domain}Entity.java`) 존재
2. Entity에 `toDomain()` 메서드 존재 — 반환 타입이 대응 domain Record
3. Entity에 `from({Domain} domain)` static 메서드 존재 — 파라미터가 대응 domain Record
4. Record의 모든 필드가 Entity에 대응 컬럼으로 존재 (createdAt/updatedAt은 BaseTimeEntity에서 상속)

**방법**:
1. `git diff --name-only`로 새로 생성된 파일 식별
2. `apps/domain/.../domain/` 하위 Record 파일을 Read
3. 대응 Entity 파일을 Read
4. 필드명과 메서드를 교차 비교

---

## [P0] 3. Repository 인터페이스-구현 쌍 검증

**양쪽 동시 읽기**: Repository interface와 CoreRepository를 함께 열어 비교한다.

검증 항목:
1. `apps/domain/.../service/{Domain}Repository.java` 인터페이스마다 `storage/db-core/.../core/{domain}/{Domain}CoreRepository.java` 존재
2. CoreRepository가 `implements {Domain}Repository` 선언
3. 인터페이스의 모든 메서드가 CoreRepository에 `@Override`로 구현
4. CoreRepository 내부에서 `toDomain()`/`from()` 변환이 올바르게 호출

**방법**:
1. Repository interface 파일을 Read — 메서드 시그니처 추출
2. CoreRepository 파일을 Read — implements 확인 + 메서드 구현 확인
3. 메서드 이름과 반환 타입 1:1 매칭 검증

---

## [P1] 4. Facade @Transactional 경계 검증

Facade 클래스(`*Facade.java`, `*AdminFacade.java`)를 읽고 검증:

1. 클래스 레벨에 `@Transactional(readOnly = true)` 존재하는가
2. 데이터 변경 메서드(이름에 create, save, update, delete, patch 포함)에 `@Transactional` 오버라이드가 있는가
3. `@Transactional` 없는 public 메서드가 여러 Repository 호출을 하지 않는가 (트랜잭션 일관성)

**위반 시 리포트**:
```
[P1] Facade @Transactional 누락
- 파일: {파일경로}:{라인번호}
- 위반: 변경 메서드 "{메서드명}"에 @Transactional 오버라이드 없음
- 수정: 메서드에 @Transactional 어노테이션 추가
```

---

## [P1] 5. Controller 호출 패턴 검증

Controller 파일을 읽고 검증:

1. Controller에 주입된 의존성 타입 확인 (Facade, Service, 기타)
2. 하나의 Controller 메서드 내에서 2개 이상의 Service를 직접 호출하면 → Facade로 분리 필요
3. 단순 조회(get/find)에서 단일 Service 직접 호출은 허용

**방법**: Controller의 `@RequiredArgsConstructor` 필드와 각 메서드 본문을 분석.

---

## [P0] 6. 빌드 및 테스트 검증

순서대로 실행:

```bash
# 1. 컴파일 확인
./gradlew compileJava

# 2. 포맷팅 확인 (실패 시 spotlessApply 제안)
./gradlew spotlessCheck

# 3. 테스트 실행
./gradlew test
```

**컴파일 실패 시**: 에러 메시지에서 파일:라인과 원인을 추출하여 리포트에 포함.
**테스트 실패 시**: 실패 테스트 클래스명 + 에러 메시지를 리포트에 포함. 경계면 검증은 별도 수행.

---

## 리포트 형식

```markdown
# QA 검증 리포트

## 검증 결과 요약

| # | 항목 | 우선순위 | 결과 | 위반 수 |
|---|------|---------|------|---------|
| 1 | Domain 순수성 | P0 | PASS/FAIL | N |
| 2 | Record-Entity 매핑 | P0 | PASS/FAIL | N |
| 3 | Repository 쌍 | P0 | PASS/FAIL | N |
| 4 | Facade @Transactional | P1 | PASS/FAIL | N |
| 5 | Controller 호출 패턴 | P1 | PASS/FAIL | N |
| 6 | 빌드/테스트 | P0 | PASS/FAIL | N |

## 위반 상세

### [{우선순위}] {항목명}
- **파일**: {경로}:{라인}
- **위반**: {구체적 내용}
- **수정**: {수정 방법}

## 최종 판정: PASS / FAIL
```
