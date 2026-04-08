# 코드 패턴 레퍼런스

yogieat-server의 실제 코드에서 추출한 패턴. 새 코드 작성 시 이 패턴을 따른다.

---

## 1. Domain Model (Java Record)

```java
// apps/domain/src/main/java/com/yogieat/{domain}/domain/{Domain}.java
package com.yogieat.gathering.domain;

import com.yogieat.common.Region;
import com.yogieat.gathering.domain.value.TimeSlot;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record Gathering(
        Long id,
        String accessKey,
        String title,
        LocalDate scheduledDate,
        TimeSlot timeSlot,
        Region region,
        Integer peopleCount,
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    // 도메인 로직은 Record 메서드로 표현
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
```

**규칙**: JPA 어노테이션 금지. 순수 Java Record. 도메인 로직만 포함.

---

## 2. Repository Interface

```java
// apps/domain/src/main/java/com/yogieat/{domain}/service/{Domain}Repository.java
package com.yogieat.gathering.service;

import com.yogieat.gathering.domain.Gathering;
import java.util.Optional;

public interface GatheringRepository {
    Optional<Gathering> findById(Long id);
    Optional<Gathering> findByAccessKey(String accessKey);
    Gathering save(Gathering gathering);
}
```

**규칙**: 도메인 모델(Record)만 사용. JPA 관련 타입 금지.

---

## 3. Entity (toDomain + from 패턴)

```java
// storage/db-core/src/main/java/com/yogieat/datasource/db/core/{domain}/{Domain}Entity.java
@Entity
@Table(name = "gathering")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GatheringEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String accessKey;
    private String title;
    // ... 필드들

    // Domain → Entity 변환 (static factory)
    public static GatheringEntity from(Gathering gathering) {
        GatheringEntity entity = new GatheringEntity();
        entity.id = gathering.id();
        entity.accessKey = gathering.accessKey();
        entity.title = gathering.title();
        // ... 나머지 필드
        return entity;
    }

    // Entity → Domain 변환 (instance method)
    public static Gathering toDomain(GatheringEntity entity) {
        return new Gathering(
                entity.getId(),
                entity.getAccessKey(),
                entity.getTitle(),
                // ... 나머지 필드
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
```

**규칙**: `toDomain()`은 static 메서드 (GatheringEntity를 인자로 받음). `from()`은 static factory. 항상 쌍으로 존재해야 한다.

---

## 4. Core Repository (implements 패턴)

```java
// storage/db-core/src/main/java/com/yogieat/datasource/db/core/{domain}/{Domain}CoreRepository.java
@Repository
@RequiredArgsConstructor
public class GatheringCoreRepository implements GatheringRepository {
    private final GatheringJpaRepository gatheringJpaRepository;
    private final JPAQueryFactory jpaQueryFactory;  // QueryDSL 사용 시

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

**규칙**: `implements {Domain}Repository`. 내부에서 Entity ↔ Domain 변환 수행. JpaRepository와 QueryDSL은 이 클래스 내부에서만 사용.

---

## 5. JPA Repository

```java
// storage/db-core/src/main/java/com/yogieat/datasource/db/core/{domain}/{Domain}JpaRepository.java
public interface GatheringJpaRepository extends JpaRepository<GatheringEntity, Long> {
    Optional<GatheringEntity> findByAccessKey(String accessKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GatheringEntity g WHERE g.accessKey = :accessKey")
    Optional<GatheringEntity> findByAccessKeyForUpdate(@Param("accessKey") String accessKey);
}
```

---

## 6. Facade (@Transactional 패턴)

```java
// apps/domain/src/main/java/com/yogieat/{domain}/facade/{Domain}AdminFacade.java
// 또는 apps/domain/src/main/java/com/yogieat/{domain}/service/{Domain}Facade.java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 클래스 레벨: 읽기 전용
public class RestaurantAdminFacade {
    private final RestaurantService restaurantService;

    // 읽기 메서드: 클래스 레벨 @Transactional(readOnly = true) 상속
    public RestaurantAdminListResult getPageRestaurants(...) {
        return restaurantService.findAdminRestaurants(...);
    }

    // 쓰기 메서드: @Transactional 오버라이드
    @Transactional
    public RestaurantAdminResult.Create createRestaurant(RestaurantCommand.Create command) {
        return restaurantService.createRestaurant(command);
    }

    @Transactional
    public void deleteRestaurantBy(Long restaurantId) {
        restaurantService.deleteBy(restaurantId);
    }
}
```

---

## 7. Controller + DTO

```java
// apps/api/src/main/java/com/yogieat/controller/v1/{domain}/{Domain}Controller.java
@Tag(name = "API 그룹명", description = "설명")
@RestController
@RequestMapping("/api/v1/{domain-plural}")
@RequiredArgsConstructor
public class GatheringController {
    private final GatheringFacade gatheringFacade;
    private final GatheringService gatheringService;  // 단순 조회 시 Service 직접 호출 허용

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

**Request DTO 패턴**: `toCommand()` 메서드로 Command Record 변환
```java
public record CreateGatheringRequest(
        @NotBlank String title,
        @NotNull LocalDate scheduledDate
) {
    public GatheringCommand.Create toCommand() {
        return new GatheringCommand.Create(title, scheduledDate);
    }
}
```

**Response DTO 패턴**: `from()` static factory로 Result → Response 변환
```java
public record CreateGatheringResponse(
        String accessKey
) {
    public static CreateGatheringResponse from(GatheringResult.Create result) {
        return new CreateGatheringResponse(result.accessKey());
    }
}
```

---

## 8. Command/Result (Nested Record 패턴)

```java
// Command는 서비스 패키지에 위치
public class GatheringCommand {
    public record Create(String title, LocalDate scheduledDate, ...) {}
    public record Update(String title, ...) {}
}

// Result는 domain/result 패키지에 위치
public class GatheringResult {
    public record Create(String accessKey, ...) {}
    public record ParticipantCount(int current, int total) {}
}
```

---

## 9. 모듈 의존성 방향

```
apps:api → apps:domain, storage:db-core, external:*, support:*
apps:admin → apps:domain, storage:db-core, external:*, support:*
apps:domain → (프레임워크 최소 의존만)
storage:db-core → apps:domain (도메인 모델 import만)
external:* → apps:domain (도메인 모델 import만)
```

**위반 사례**: apps:domain에서 `com.yogieat.datasource.*` import → 컴파일은 되지만 아키텍처 위반
