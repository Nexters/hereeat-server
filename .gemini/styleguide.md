# Yogieat Server - Code Review Style Guide

This style guide defines the coding standards and review priorities for the Yogieat Server project.

---

## Project Context

- **Tech Stack**: Spring Boot 3.5, Java 25, PostgreSQL, JPA, QueryDSL
- **Architecture**: Multi-Module Clean Architecture
- **Environment**: Single Instance (단일 인스턴스)
- **Code Formatter**: Spotless
- **Language**: All review comments must be written in Korean (ko-KR)

---

## Architecture Overview

```
Controller (apps:api)
    ↓
Facade (apps:domain) - @Transactional boundary
    ↓
Service (apps:domain) - Business logic
    ↓
Repository Interface (apps:domain) ← Implementation → Repository Impl (storage:db-core)
    ↓
Entity (storage:db-core)
```

**Key Patterns:**
- **Domain**: Java Record (immutable)
- **Command/Result**: Nested Record pattern
- **Repository**: Interface (domain) + Implementation (storage)
- **Validation**: Separate Validator classes

---

## Review Priorities

### 🔴 P0 - Critical (Must Fix)

Focus on potential risks in single-instance environments:

#### 1. Race Condition & Concurrency

**Pattern to Detect:**
```java
// ❌ Bad - Race Condition
long count = repository.count~(~);
if (count >= limit) { throw ~ }
repository.save(~);
// Without: @Lock, synchronized, LockManager
```

**Required Fix:**
- Use `@Lock(LockModeType.PESSIMISTIC_WRITE)`
- Or use `LockManager` with key-based locking
- Add Concurrency Test

#### 2. Transaction Management

**Pattern to Detect:**
```java
// ❌ Bad - Missing @Transactional in Facade
public ~Result ~(~Command command) {
    service1.~(~);
    service2.~(~);
}
```

**Required Fix:**
- Add `@Transactional` to Facade methods that call multiple Services
- Use `@Transactional(readOnly = true)` for read-only methods

#### 3. Security Issues

**Pattern to Detect:**
```java
// ❌ Bad - SQL Injection
@Query("... + variable + ...")
@Query(value = "... '" + param + "' ...", nativeQuery = true)
```

**Required Fix:**
- Use parameter binding: `@Query("... WHERE field = :param")`
- Use `@Param` annotation

#### 4. Memory Leak

**Pattern to Detect:**
```java
// ❌ Bad - Unbounded cache
private static final Map<String, Object> cache = new HashMap<>();
```

**Required Fix:**
- Add cache eviction policy (TTL, max size)
- Use `@Cacheable` with proper configuration

---

### 🟠 P1 - High (Strongly Recommended)

#### 1. N+1 Problem

**Pattern to Detect:**
```java
// ❌ Bad
for (~ : list) {
    repository.find~(~);
}
// Or
list.stream().map(~ -> repository.find~(~))
```

**Required Fix:**
- Use Fetch Join: `@Query("SELECT ... JOIN FETCH ...")`
- Use `@BatchSize`
- Use QueryDSL with join

#### 2. LazyInitializationException

**Pattern to Detect:**
```java
// ❌ Bad - Lazy loading outside transaction
@Transactional(readOnly = true)
public Domain getDomain(Long id) {
    return repository.findById(id).get();
}

// In Controller (no @Transactional)
domain.getRelation().size() // Exception!
```

**Required Fix:**
- Use Fetch Join
- Convert to DTO within transaction
- Use `@EntityGraph`

#### 3. Missing Timeout

**Pattern to Detect:**
```java
// ❌ Bad - No timeout
@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();
}
```

**Required Fix:**
- Set connect timeout (3-5 seconds)
- Set read timeout (10-30 seconds)

---

### 🟡 P2 - Medium (Recommended)

#### 1. Architecture Violation

**Pattern to Detect:**
```java
// ❌ Bad - domain importing storage
// In apps:domain
import com.yogieat.datasource.db.core.*;
```

**Required Fix:**
- Use Domain models instead of Entities
- Access storage through Repository interfaces

#### 2. Missing Index

**Pattern to Detect:**
```java
// ❌ Bad - Query on non-indexed column
Optional<~> findBy{FieldWithoutIndex}(~);
```

**Required Fix:**
- Add `@Index` annotation to Entity
- Create database index

#### 3. Naming Convention Violation

**Expected Naming:**
- Controller: `{Domain}Controller`
- Facade: `{Domain}Facade`
- Service: `{Domain}Service`
- Repository Interface: `{Domain}Repository`
- Repository Impl: `{Domain}CoreRepository`
- Entity: `{Domain}Entity`
- Command: `{Domain}Command`
- Result: `{Domain}Result`

---

### 🟢 P3 - Low (Optional)

#### 1. Code Style

- Import order: Java/Jakarta → Spring → External Libraries → Internal
- Use Spotless for formatting
- Remove unused imports

#### 2. Documentation

- Add JavaDoc for complex business logic
- Add `@Operation` for API endpoints

---

## Review Rules

### Maximum Comments

Provide up to **5 comments** per PR, prioritized by severity:
1. P0 (Critical) issues first
2. P1 (High) issues
3. P2 (Medium) issues
4. P3 (Low) issues only if less than 5 critical issues

### Comment Format

All comments must follow this format:

```
[Priority] Category: Issue Title

**문제점:**
{Specific problem description in Korean}

**위험성:**
{Potential risk - for P0/P1 only}

**제안:**
- {Solution 1}
- {Solution 2}

**예시:**
```java
// ❌ Bad
{Current code}

// ✅ Good
{Improved code}
```

**파일:** {file_path}:{line_number}
```

### Language Requirement

- **All review comments must be written in Korean (ko-KR)**
- Technical terms can remain in English (e.g., Race Condition, N+1 Problem)
- Code examples can use English variable names

### Prohibited Feedback

Do NOT include:
- General summaries or explanations of changes
- Praise like "잘했어요", "좋은 코드네요"
- Questions about the PR author's intentions without suggesting improvements
- Comments on issues already covered in other reviews

---

## Specific Patterns to Review

### 1. Concurrency Control

**Check for:**
- `count()` + `if` + `save()` pattern without locking
- Concurrent updates to shared resources
- Missing `@Version` for optimistic locking

**Example Comment:**
```
[🔴 P0] Concurrency: Race Condition 발생 가능

**문제점:**
동시에 여러 요청이 들어올 경우 `peopleCount`를 초과하여 참여자가 등록될 수 있습니다.

**위험성:**
단일 인스턴스 환경에서도 멀티스레드 요청으로 인해 Race Condition이 발생합니다.

**제안:**
- `@Lock(LockModeType.PESSIMISTIC_WRITE)` 사용
- 또는 `LockManager`로 accessKey 기반 락 적용

**예시:**
```java
// ❌ Bad
long count = participantRepository.countByGatheringId(gatheringId);
if (count >= gathering.getPeopleCount()) {
    throw new CustomException(ErrorCode.GATHERING_FULL);
}
participantRepository.save(participant);

// ✅ Good
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT g FROM GatheringEntity g WHERE g.accessKey = :accessKey")
Optional<GatheringEntity> findByAccessKeyWithLock(@Param("accessKey") String accessKey);
```

**파일:** apps/domain/src/main/java/com/yogieat/participant/service/ParticipantService.java:45
```

### 2. Transaction Boundaries

**Check for:**
- Facade methods without `@Transactional`
- Multiple Service calls without transaction
- External API calls inside transaction

### 3. Performance Issues

**Check for:**
- Repository calls inside loops
- `findAll()` without pagination
- Missing Fetch Join for associations

### 4. Security Vulnerabilities

**Check for:**
- String concatenation in `@Query`
- Sensitive data in logs
- Missing input validation

---

## Code Quality Standards

### DO's ✅

- Use Java Record for immutable Domain models
- Use Command/Result pattern for layer communication
- Use Repository interfaces for dependency inversion
- Separate validation logic into Validator classes
- Use `@Transactional(readOnly = true)` for read operations
- Add Swagger annotations for API documentation
- Write tests for concurrency scenarios

### DON'Ts ❌

- Don't add JPA annotations to Domain models
- Don't return Entity from Controller
- Don't call transactional Service methods from another Service
- Don't declare Repository interfaces in storage module
- Don't create circular dependencies
- Don't use magic numbers (use constants)
- Don't catch generic `Exception`

---

## Testing Requirements

When reviewing tests, check for:

- **Given-When-Then** pattern
- `@DisplayName` with Korean description
- DatabaseCleaner for test isolation
- Fixture pattern usage
- Concurrency tests for critical sections

**Example:**
```java
@Test
@DisplayName("동시에 10명이 참여 시도 시 peopleCount(4)를 초과하지 않는다")
void concurrentParticipation_shouldNotExceedPeopleCount() {
    // Given-When-Then
}
```

---

## Additional Context

### Multi-Module Dependencies

**Valid:**
```
apps:api → apps:domain, storage:db-core, external:*, support:*
apps:domain → (no external dependencies)
storage:db-core → apps:domain (Domain models only)
```

**Invalid:**
```
apps:domain → storage:db-core (직접 의존 금지)
apps:domain → external:* (외부 통합은 apps:api에서)
```

### Transaction Isolation

Single instance environment requires careful transaction management:
- Keep transactions as short as possible
- Avoid external API calls inside transactions
- Use proper isolation levels
- Consider distributed locking for critical sections

---

## Summary

When reviewing PRs for Yogieat Server:

1. **Prioritize Critical Issues**: Focus on P0 (Race Condition, Security, Transaction)
2. **Provide Specific Feedback**: Include file path, line number, and concrete solutions
3. **Use Korean Language**: All comments in Korean except code examples
4. **Limit to 5 Comments**: Select the most impactful issues
5. **Include Code Examples**: Show both bad and good patterns
6. **Consider Single-Instance Risks**: Pay special attention to concurrency and memory issues

Thank you for maintaining code quality! 🙏
