# Code Review Guide - Yogieat Server

개발자를 위한 종합 코드 리뷰 가이드입니다. 이 문서는 코딩 스타일, 잠재적 위험, 리뷰 체크리스트를 통합하여 제공합니다.

---

## 📋 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [Multi-Module Architecture](#multi-module-architecture)
3. [코딩 스타일](#코딩-스타일)
4. [잠재적 위험 (단일 인스턴스 환경)](#잠재적-위험)
5. [리뷰 체크리스트](#리뷰-체크리스트)

---

## 프로젝트 개요

### Tech Stack

- **Framework**: Spring Boot 3.5
- **Language**: Java 25
- **Database**: PostgreSQL with JPA (Hibernate)
- **Build Tool**: Gradle
- **Architecture**: Multi-Module Clean Architecture
- **Code Formatter**: Spotless
- **Libraries**: Lombok, QueryDSL

### 리뷰 우선순위

1. 🔴 **P0 - Critical**: Race Condition, Security, Transaction, Memory Leak
2. 🟠 **P1 - High**: N+1 Problem, Timeout, Performance
3. 🟡 **P2 - Medium**: Architecture Violation, Missing Index, Convention
4. 🟢 **P3 - Low**: Code Style, Documentation

---

## Multi-Module Architecture

### 모듈 구조

```
apps/
├── api/          # REST API 진입점 (Controller, Request/Response DTO)
└── domain/       # 비즈니스 로직 (Service, Facade, Domain, Command/Result)

external/
├── ai/           # AI 서비스 통합 (Gemini API)
└── kakao/        # Kakao API 통합

storage/
└── db-core/      # 데이터베이스 접근 (JPA Entity, Repository 구현체)

support/
├── logging/      # 로깅 설정
├── monitoring/   # 모니터링 (Actuator, Micrometer)
└── swagger/      # API 문서화 (SpringDoc OpenAPI)
```

### Clean Architecture 계층 흐름

```
Controller (apps:api)
    ↓
Facade (apps:domain) - @Transactional 경계
    ↓
Service (apps:domain) - 비즈니스 로직
    ↓
Repository Interface (apps:domain) ← 구현 → Repository Impl (storage:db-core)
    ↓
Entity (storage:db-core)
```

### 의존성 원칙

**✅ 허용:**
- `apps:api` → 모든 하위 모듈
- `apps:domain` → 외부 모듈에 의존하지 않음 (순수 비즈니스 로직)
- `storage:db-core` → `apps:domain` (Domain 모델만 참조)

**❌ 금지:**
- `apps:domain` → `storage:db-core` 직접 의존
- `apps:domain` → `external:*` 직접 의존

---

## 코딩 스타일

### 1. Naming Convention

#### Package Naming

```java
com.yogieat.{domain}.{layer}

// 예시
com.yogieat.gathering.service
com.yogieat.gathering.domain
com.yogieat.controller.v1.gathering
```

#### Class Naming

| 계층 | 네이밍 규칙 | 예시 |
|------|------------|------|
| Controller | `{Domain}Controller` | `GatheringController` |
| Facade | `{Domain}Facade` | `GatheringFacade` |
| Service | `{Domain}Service` | `GatheringService` |
| Validator | `{Domain}Validator` | `GatheringValidator` |
| Repository Interface | `{Domain}Repository` | `GatheringRepository` |
| Repository Impl | `{Domain}CoreRepository` | `GatheringCoreRepository` |
| JPA Repository | `{Domain}JpaRepository` | `GatheringJpaRepository` |
| Entity | `{Domain}Entity` | `GatheringEntity` |
| Domain | `{Domain}` | `Gathering` |
| Command | `{Domain}Command` | `GatheringCommand` |
| Result | `{Domain}Result` | `GatheringResult` |

### 2. Domain Model (Record)

**Java Record 사용** (불변 객체):

```java
public record Gathering(
        Long id,
        String accessKey,
        String title,
        LocalDate scheduledDate,
        TimeSlot timeSlot,
        Region region,
        Integer peopleCount,
        LocalDateTime deletedAt
) {
    // 비즈니스 로직 메서드 포함 가능
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
```

### 3. Command/Result Pattern

**Nested Record로 관리:**

```java
// Command
public record GatheringCommand() {
    public record Create(
            Integer peopleCount,
            LocalDate scheduledDate,
            TimeSlot timeSlot,
            Region region
    ) {
        public static Create of(/* params */) {
            return new Create(/* ... */);
        }
    }
}

// Result
public record GatheringResult() {
    public record Create(
            Long gatheringId,
            String accessKey
    ) {
        public static Create of(Gathering gathering) {
            return new Create(gathering.id(), gathering.accessKey());
        }
    }
}
```

### 4. Controller Layer

```java
@Tag(name = "🙋 Gathering API", description = "모임 관련 API")
@RestController
@RequestMapping("/api/v1/gatherings")
@RequiredArgsConstructor
public class GatheringController {

    private final GatheringFacade gatheringFacade;

    @Operation(summary = "모임 생성", description = "사용자가 모임을 생성합니다.")
    @PostMapping
    public CreateGatheringResponse createGathering(
            @RequestBody @Valid CreateGatheringRequest request
    ) {
        GatheringResult.Create result = gatheringFacade.createGathering(request.toCommand());
        return CreateGatheringResponse.from(result);
    }
}
```

### 5. Facade Layer

**Transaction 경계 설정:**

```java
@Service
@RequiredArgsConstructor
public class GatheringFacade {

    private final GatheringService gatheringService;

    @Transactional
    public GatheringResult.Create createGathering(GatheringCommand.Create command) {
        Gathering gathering = gatheringService.create(command);
        return GatheringResult.Create.of(gathering);
    }
}
```

### 6. Service Layer

```java
@Service
@RequiredArgsConstructor
public class GatheringService {
    private final GatheringValidator gatheringValidator;
    private final GatheringRepository gatheringRepository;

    @Transactional
    public Gathering create(GatheringCommand.Create command) {
        gatheringValidator.validateCreate(command);

        Gathering gathering = new Gathering(/* ... */);
        return gatheringRepository.save(gathering);
    }

    @Transactional(readOnly = true)
    public Gathering getGatheringByAccessKey(String accessKey) {
        Gathering gathering = gatheringRepository.findByAccessKey(accessKey)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
        gatheringValidator.validateGatheringNotDeleted(gathering);
        return gathering;
    }
}
```

### 7. Repository Pattern

```java
// Interface (apps:domain)
public interface GatheringRepository {
    Optional<Gathering> findById(Long id);
    Optional<Gathering> findByAccessKey(String accessKey);
    Gathering save(Gathering gathering);
}

// Implementation (storage:db-core)
@Repository
@RequiredArgsConstructor
public class GatheringCoreRepository implements GatheringRepository {
    private final GatheringJpaRepository gatheringJpaRepository;

    @Override
    public Optional<Gathering> findById(Long id) {
        return gatheringJpaRepository.findById(id)
                .map(GatheringEntity::toDomain);
    }

    @Override
    public Gathering save(Gathering gathering) {
        GatheringEntity entity = GatheringEntity.from(gathering);
        GatheringEntity savedEntity = gatheringJpaRepository.save(entity);
        return GatheringEntity.toDomain(savedEntity);
    }
}
```

### 8. Entity Layer

```java
@Getter
@Entity
@Table(name = "t_gathering")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GatheringEntity extends BaseEntity {

    @Column(name = "access_key")
    private String accessKey;

    @Column(name = "title")
    private String title;

    @Builder(access = AccessLevel.PRIVATE)
    public GatheringEntity(/* params */) {
        // ...
    }

    public static GatheringEntity from(Gathering gathering) {
        return GatheringEntity.builder()
                .accessKey(gathering.accessKey())
                .title(gathering.title())
                .build();
    }

    public static Gathering toDomain(GatheringEntity entity) {
        return new Gathering(/* ... */);
    }
}
```

---

## 잠재적 위험

### 🔴 P0 - Critical

#### 1. Race Condition

**문제점:**
단일 인스턴스 환경에서도 멀티스레드 요청으로 Race Condition 발생

**탐지 패턴:**
```java
// ❌ Bad
long count = participantRepository.countByGatheringId(gatheringId);
if (count >= gathering.getPeopleCount()) {
    throw new CustomException(ErrorCode.GATHERING_FULL);
}
participantRepository.save(participant); // 동시 요청 시 초과 가능
```

**해결 방법:**
```java
// ✅ Good - Pessimistic Lock
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT g FROM GatheringEntity g WHERE g.accessKey = :accessKey")
Optional<GatheringEntity> findByAccessKeyWithLock(@Param("accessKey") String accessKey);

// ✅ Good - LockManager
public void participate(String accessKey) {
    synchronized (lockManager.getLock(accessKey)) {
        long count = participantRepository.countByGatheringId(gatheringId);
        if (count >= gathering.getPeopleCount()) {
            throw new CustomException(ErrorCode.GATHERING_FULL);
        }
        participantRepository.save(participant);
    }
}
```

#### 2. Missing @Transactional

**탐지 패턴:**
```java
// ❌ Bad - Facade에 @Transactional 누락
public GatheringResult.Create createGathering(GatheringCommand.Create command) {
    Gathering gathering = gatheringService.create(command);
    participantService.createHost(gathering.id()); // 중간 실패 시 일관성 깨짐
    return GatheringResult.Create.of(gathering);
}
```

**해결 방법:**
```java
// ✅ Good
@Transactional
public GatheringResult.Create createGathering(GatheringCommand.Create command) {
    Gathering gathering = gatheringService.create(command);
    participantService.createHost(gathering.id());
    return GatheringResult.Create.of(gathering);
}
```

#### 3. SQL Injection

**탐지 패턴:**
```java
// ❌ Bad
@Query(value = "SELECT * FROM t_gathering WHERE access_key = '" + accessKey + "'", nativeQuery = true)
List<GatheringEntity> findByAccessKey(String accessKey);
```

**해결 방법:**
```java
// ✅ Good
@Query(value = "SELECT * FROM t_gathering WHERE access_key = :accessKey", nativeQuery = true)
List<GatheringEntity> findByAccessKey(@Param("accessKey") String accessKey);
```

#### 4. Memory Leak

**탐지 패턴:**
```java
// ❌ Bad - Static Collection 무한 증가
public class CacheManager {
    private static final Map<String, Object> cache = new HashMap<>();

    public void put(String key, Object value) {
        cache.put(key, value); // 메모리 계속 증가
    }
}
```

**해결 방법:**
```java
// ✅ Good - Cache Eviction 정책
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(1000));
        return cacheManager;
    }
}
```

### 🟠 P1 - High

#### 1. N+1 Problem

**탐지 패턴:**
```java
// ❌ Bad
public List<GatheringResponse> getAllGatherings() {
    List<Gathering> gatherings = gatheringRepository.findAll();
    return gatherings.stream()
        .map(g -> {
            long count = participantRepository.countByGatheringId(g.id()); // N번 쿼리
            return new GatheringResponse(g, count);
        })
        .toList();
}
```

**해결 방법:**
```java
// ✅ Good - QueryDSL
public List<GatheringResponse> getAllGatherings() {
    return queryFactory
        .select(Projections.constructor(
            GatheringResponse.class,
            gathering,
            participant.count()
        ))
        .from(gathering)
        .leftJoin(participant).on(participant.gatheringId.eq(gathering.id))
        .groupBy(gathering.id)
        .fetch();
}

// ✅ Good - Fetch Join
@Query("SELECT g FROM GatheringEntity g LEFT JOIN FETCH g.participants")
List<GatheringEntity> findAllWithParticipants();
```

#### 2. Missing Timeout

**탐지 패턴:**
```java
// ❌ Bad
@Bean
public RestTemplate restTemplate() {
    return new RestTemplate(); // 무한 대기 가능
}
```

**해결 방법:**
```java
// ✅ Good
@Bean
public RestTemplate restTemplate() {
    HttpComponentsClientHttpRequestFactory factory =
        new HttpComponentsClientHttpRequestFactory();
    factory.setConnectTimeout(3000); // 3초
    factory.setReadTimeout(10000);   // 10초
    return new RestTemplate(factory);
}
```

#### 3. LazyInitializationException

**탐지 패턴:**
```java
// ❌ Bad
@Transactional(readOnly = true)
public Gathering getGathering(Long id) {
    return gatheringRepository.findById(id).get();
}

// Controller (트랜잭션 외부)
public GatheringResponse get(Long id) {
    Gathering gathering = gatheringService.getGathering(id);
    return new GatheringResponse(
        gathering,
        gathering.getParticipants().size() // LazyInitializationException!
    );
}
```

**해결 방법:**
```java
// ✅ Good - Fetch Join
@Query("SELECT g FROM GatheringEntity g LEFT JOIN FETCH g.participants WHERE g.id = :id")
Optional<GatheringEntity> findByIdWithParticipants(@Param("id") Long id);

// ✅ Good - DTO 변환 (트랜잭션 내)
@Transactional(readOnly = true)
public GatheringResponse getGathering(Long id) {
    Gathering gathering = gatheringRepository.findByIdWithParticipants(id).get();
    return new GatheringResponse(gathering, gathering.getParticipants().size());
}
```

### 🟡 P2 - Medium

#### 1. Architecture Violation

**탐지 패턴:**
```java
// ❌ Bad - apps:domain에서 storage 직접 import
import com.yogieat.datasource.db.core.gathering.GatheringEntity;

public Gathering create(GatheringCommand.Create command) {
    GatheringEntity entity = new GatheringEntity(...);
    return repository.save(entity);
}
```

**해결 방법:**
```java
// ✅ Good
public Gathering create(GatheringCommand.Create command) {
    Gathering gathering = new Gathering(...);
    return gatheringRepository.save(gathering);
}
```

#### 2. Missing Index

**탐지 패턴:**
```java
// ❌ Bad - accessKey에 인덱스 없음
Optional<Gathering> findByAccessKey(String accessKey);
```

**해결 방법:**
```java
// ✅ Good
@Entity
@Table(name = "t_gathering", indexes = {
    @Index(name = "idx_access_key", columnList = "access_key")
})
public class GatheringEntity { }
```

---

## 리뷰 체크리스트

### 1. Architecture Compliance

- [ ] `apps:domain`이 `storage:db-core`를 직접 의존하지 않는가?
- [ ] Repository 인터페이스는 `apps:domain`에, 구현체는 `storage:db-core`에 위치하는가?
- [ ] Entity는 `storage:db-core`에만 존재하는가?
- [ ] Controller → Facade → Service → Repository 흐름을 따르는가?
- [ ] Service 간 순환 참조가 없는가?

### 2. Naming Convention

- [ ] Controller: `{Domain}Controller` 형식인가?
- [ ] Facade: `{Domain}Facade` 형식인가?
- [ ] Service: `{Domain}Service` 형식인가?
- [ ] Repository Interface: `{Domain}Repository` 형식인가?
- [ ] Repository Impl: `{Domain}CoreRepository` 형식인가?
- [ ] Entity: `{Domain}Entity` 형식인가?

### 3. Concurrency & Race Condition

- [ ] 동시성 제어가 필요한 비즈니스 로직에 락이 적용되어 있는가?
- [ ] `@Lock` 또는 `synchronized` 블록 사용 여부
- [ ] LockManager를 사용하여 키 기반 락 분리를 하는가?
- [ ] 동시 업데이트 가능성이 있는 필드에 `@Version` 사용 여부

### 4. Transaction Management

- [ ] Facade 계층에 `@Transactional`이 선언되어 있는가?
- [ ] 여러 Repository 호출 시 트랜잭션으로 묶여 있는가?
- [ ] 트랜잭션 내 외부 API 호출이 없는가?
- [ ] 조회 메서드에 `@Transactional(readOnly = true)`가 있는가?

### 5. Database Performance

- [ ] 반복문 내에서 Repository 호출이 없는가?
- [ ] Fetch Join, `@EntityGraph`, 또는 `@BatchSize`를 사용하는가?
- [ ] `findAll()`을 무분별하게 사용하지 않는가?
- [ ] 대량 데이터 조회 시 Pageable을 사용하는가?
- [ ] `findBy~` 메서드의 조회 컬럼에 인덱스가 존재하는가?

### 6. Exception Handling

- [ ] `CustomException` + `ErrorCode` 패턴을 사용하는가?
- [ ] Generic Exception을 직접 throw하지 않는가?
- [ ] 예외 메시지에 민감 정보가 포함되지 않는가?

### 7. Security

- [ ] Native Query에 파라미터 바인딩을 사용하는가?
- [ ] 로그에 비밀번호, 토큰 등 민감 정보가 포함되지 않는가?
- [ ] `@Valid` 어노테이션으로 입력 검증을 하는가?

### 8. Memory & Resource

- [ ] Static Collection 사용 시 크기 제한이 있는가?
- [ ] Cache에 Eviction 정책이 설정되어 있는가?
- [ ] `try-with-resources`로 리소스를 안전하게 닫는가?

### 9. Testing

- [ ] Given-When-Then 패턴을 따르는가?
- [ ] `@DisplayName`으로 한글 설명이 있는가?
- [ ] DatabaseCleaner로 테스트 격리가 보장되는가?
- [ ] Concurrency Test가 필요한 경우 작성되었는가?

### 10. Code Quality

- [ ] Magic Number를 상수로 추출했는가?
- [ ] 중복 코드가 3번 이상 반복되지 않는가?
- [ ] 메서드가 30줄 이하인가?
- [ ] `Optional`을 적절히 사용하는가?
- [ ] Stream, Connection 등이 `try-with-resources`로 닫히는가?

---

## Best Practices

### DO's ✅

- Record 사용으로 불변성 보장
- Command/Result 패턴으로 계층 간 데이터 전달
- Repository 인터페이스로 의존성 역전
- Validator 클래스로 검증 로직 분리
- `@Transactional(readOnly = true)` 적극 활용
- Spotless로 코드 포맷팅 자동화
- Swagger 어노테이션으로 API 문서화
- JavaDoc 주석으로 복잡한 로직 설명
- Concurrency Test 작성

### DON'Ts ❌

- Domain 모델에 JPA 어노테이션 추가 금지
- Entity를 Controller에서 직접 반환 금지
- Service에서 다른 Service의 @Transactional 메서드 호출 주의
- Repository 인터페이스를 storage 모듈에 선언 금지
- 순환 의존성 발생 금지
- Magic Number 사용 금지 (상수화 필수)
- Generic Exception 사용 금지
- 과도한 주석 지양 (코드로 설명)

---

## Code Formatting (Spotless)

```gradle
spotless {
    java {
        target("**/*.java")
        targetExclude("**/generated/**", "**/build/generated/**")
        importOrder()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
```

**Import 순서:**
1. Java/Jakarta 표준 라이브러리
2. Spring Framework
3. 외부 라이브러리
4. 내부 패키지 (com.yogieat)

---

## 참고

더 자세한 내용은 다음 문서를 참고하세요:
- `.gemini/README.md`: Gemini Code Assist 설정 및 사용 가이드
- `.gemini/styleguide.md`: Gemini가 읽는 스타일 가이드
