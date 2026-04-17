---
name: spring-multimodule-implement
description: "yogieat-server 멀티모듈 클린 아키텍처의 구현 가이드. 새 도메인이나 기능을 추가할 때 어떤 모듈에 어떤 파일을 어떤 패턴으로 만들어야 하는지 안내한다. Spring Boot 멀티모듈, 클린 아키텍처, domain Record, Entity 매핑, Repository 인터페이스 패턴 등을 다룬다."
---

# Spring Multi-Module Implementation Guide

yogieat-server의 멀티모듈 클린 아키텍처에서 코드를 구현할 때 따르는 가이드.

## 아키텍처 흐름

```
Controller (apps:api)
    ↓ Request DTO
Facade (apps:domain) — @Transactional 경계
    ↓ Command Record
Service (apps:domain) — 비즈니스 로직
    ↓ Domain Record
Repository Interface (apps:domain) ← implements ← CoreRepository (storage:db-core)
    ↓                                                    ↓
Domain Record                                    Entity ↔ Domain (toDomain/from)
```

## 모듈별 파일 생성 레시피

### apps/domain 모듈

`apps:domain`은 프레임워크에 의존하지 않는 순수 비즈니스 로직 모듈이다.
허용 의존성: spring-context, spring-tx, spring-web, jackson-databind, slf4j-api만 가능.
JPA, QueryDSL, 외부 API 클라이언트 import는 절대 금지.

| 구성요소 | 패키지 | 네이밍 | 타입 |
|---------|--------|--------|------|
| Domain model | `com.yogieat.{domain}.domain` | `{Domain}` | Java Record |
| Value object | `com.yogieat.{domain}.domain.value` | 도메인에 맞게 | Enum 또는 Record |
| Command | `com.yogieat.{domain}.service` | `{Domain}Command` | Record (Nested) |
| Result | `com.yogieat.{domain}.domain.result` | `{Domain}Result` | Record (Nested) |
| Repository | `com.yogieat.{domain}.service` | `{Domain}Repository` | Interface |
| Service | `com.yogieat.{domain}.service` | `{Domain}Service` | `@Service` class |
| Validator | `com.yogieat.{domain}.service` | `{Domain}Validator` | `@Component` class |
| API Facade | `com.yogieat.{domain}.service` | `{Domain}Facade` | `@Service` class |
| Admin Facade | `com.yogieat.{domain}.facade` | `{Domain}AdminFacade` | `@Service` class |

### storage/db-core 모듈

`storage:db-core`는 JPA Entity와 Repository 구현을 담당한다.
`apps:domain`의 도메인 모델(Record)만 import 가능.

| 구성요소 | 패키지 | 네이밍 | 타입 |
|---------|--------|--------|------|
| Entity | `com.yogieat.datasource.db.core.{domain}` | `{Domain}Entity` | `@Entity` class |
| JPA Repository | `com.yogieat.datasource.db.core.{domain}` | `{Domain}JpaRepository` | Spring Data Interface |
| Core Repository | `com.yogieat.datasource.db.core.{domain}` | `{Domain}CoreRepository` | `@Repository` class |

### apps/api 모듈

| 구성요소 | 패키지 | 네이밍 | 타입 |
|---------|--------|--------|------|
| Controller | `com.yogieat.controller.v1.{domain}` | `{Domain}Controller` | `@RestController` |
| Request DTO | `com.yogieat.controller.v1.{domain}.request` | `{Action}{Domain}Request` | Record |
| Response DTO | `com.yogieat.controller.v1.{domain}.response` | `{Action}{Domain}Response` | Record |

## 구현 순서

반드시 이 순서를 따른다. 의존 방향이 안쪽(domain)에서 바깥쪽(api)으로 흐르기 때문이다.

1. Domain model (Record) — 핵심 도메인 객체 정의
2. Value objects — Enum, 값 객체
3. Command/Result — 레이어 간 데이터 전달 객체
4. Repository interface — 도메인 레이어의 데이터 접근 계약
5. Validator — 비즈니스 규칙 검증
6. Service — 비즈니스 로직
7. Facade — 트랜잭션 경계 + 서비스 조합
8. Entity — JPA 엔티티 (`toDomain()` + `from()` 필수)
9. JPA Repository — Spring Data 인터페이스
10. Core Repository — Repository 인터페이스 구현
11. Controller + Request/Response DTO
12. Tests

## 핵심 패턴 상세

상세 코드 패턴은 `references/patterns.md`를 참조하라. 구현 시 반드시 해당 패턴을 읽고 기존 코드와 일관되게 작성한다.

## 테스트 작성 규칙

- Given-When-Then 패턴
- `@DisplayName`은 한국어로 작성 (예: `"모임 생성 시 accessKey가 자동 생성된다"`)
- DatabaseCleaner로 테스트 격별리
- Fixture 패턴 사용
- 동시성 테스트는 `@RepeatedTest` 또는 `CountDownLatch` 활용
