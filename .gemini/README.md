# Gemini Code Assist - Yogieat Server

Gemini Code Assist를 활용한 자동 PR 코드 리뷰 시스템입니다.

---

## 📁 디렉토리 구조

```
.gemini/
├── config.yml           # Gemini 설정 (필수)
├── styleguide.md        # Gemini가 읽는 스타일 가이드 (필수)
├── code-review-guide.md # 개발자용 코드 리뷰 가이드 (참고)
└── README.md            # 이 파일 (설정 및 사용 가이드)
```

---

## 🎯 개요

### 프로젝트 정보

- **Tech Stack**: Spring Boot 3.5, Java 25, PostgreSQL, JPA, QueryDSL
- **Architecture**: Multi-Module Clean Architecture
- **Environment**: Single Instance (단일 인스턴스)
- **Review Language**: 한국어 (ko-KR)

### 리뷰 우선순위

1. 🔴 **P0 - Critical**: Race Condition, Security, Transaction 문제
2. 🟠 **P1 - High**: N+1 Problem, Performance 이슈
3. 🟡 **P2 - Medium**: Architecture Violation, Convention
4. 🟢 **P3 - Low**: Code Style

---

## 🚀 빠른 시작

### 1. GitHub App 설치 (필수)

```bash
# 브라우저에서 접속
https://github.com/apps/gemini-code-assist

# "Install" 버튼 클릭 → yogieat-server 선택 → 권한 승인
```

**두 가지 버전:**
- **Consumer** (무료): 일일 33개 PR 제한
- **Enterprise**: Google Cloud 연동, 일일 100+ PR 지원

### 2. 설정 확인

**✅ 이미 완료된 설정:**
- `config.yml`: Gemini 리뷰 설정
- `styleguide.md`: 커스텀 스타일 가이드

### 3. 테스트

```bash
# 테스트 브랜치 생성
git checkout -b test/gemini-review

# 변경 사항 추가 (예: Race Condition 패턴)
echo 'public void test() {
    long count = repository.count();
    repository.save(entity);
}' > Test.java

# Commit & Push
git add . && git commit -m "test: Gemini" && git push origin test/gemini-review

# PR 생성
gh pr create --base develop --title "Test: Gemini Code Assist"

# 2-5분 후 gemini-code-assist[bot]의 리뷰 확인
```

---

## ⚙️ 설정 상세

### config.yml

```yaml
have_fun: false
code_review:
  disable: false
  comment_severity_threshold: MEDIUM      # Medium 이상만 코멘트
  max_review_comments: 5                  # 최대 5개 (우선순위 기반)
  pull_request_opened:
    help: false
    summary: true                         # PR 요약 활성화
    code_review: true                     # 자동 리뷰 활성화
    include_drafts: false                 # Draft PR 제외
ignore_patterns:
  - ".github/"                            # CI/CD 설정
  - ".claude/"                            # Claude 설정
  - ".serena/"                            # Serena 설정
  - "**/generated/**"                     # 자동 생성 파일
  - "**/build/**"                         # 빌드 산출물
```

### styleguide.md

Gemini가 직접 읽는 파일로, 다음 내용 포함:
- 프로젝트 컨텍스트 (Spring Boot, Clean Architecture)
- P0/P1/P2/P3 우선순위 및 탐지 패턴
- 한국어 리뷰 형식
- Code Quality 표준

---

## 🤖 작동 방식

### 자동 리뷰 프로세스

```
PR opened to develop
    ↓
gemini-code-assist[bot] 트리거
    ↓
1. PR Summary 생성
    ↓
2. Code Review 실행
   - styleguide.md 참조
   - config.yml 설정 적용
    ↓
3. 최대 5개 코멘트 작성
   (P0 → P1 → P2 → P3 순서)
```

### 리뷰 댓글 예시

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

---

## 💡 수동 명령어

PR 댓글에서 사용 가능:

```bash
/gemini review          # 전체 PR 리뷰
/gemini summary         # PR 요약만 생성
/gemini help            # 도움말
```

---

## 🔧 커스터마이징

### Severity Threshold 조정

```yaml
# 더 많은 이슈 표시
comment_severity_threshold: LOW

# 중요한 이슈만 표시
comment_severity_threshold: HIGH
```

### 코멘트 수 조정

```yaml
max_review_comments: 10    # 최대 10개
```

### Draft PR 리뷰 포함

```yaml
pull_request_opened:
  include_drafts: true     # Draft PR도 리뷰
```

### Ignore Patterns 추가

```yaml
ignore_patterns:
  - "**/*Test.java"        # 테스트 파일 제외
  - "**/dto/**"            # DTO 제외
  - "**/*.sql"             # SQL 파일 제외
```

---

## 📊 주요 탐지 패턴

### 🔴 P0 - Critical

#### 1. Race Condition
```java
// 탐지 패턴
long count = repository.count~();
if (count >= limit) { throw ~ }
repository.save(~);
```

#### 2. Missing @Transactional
```java
// Facade에서 여러 Service 호출
public ~Result ~(~Command command) {
    service1.~();
    service2.~();
}
```

#### 3. SQL Injection
```java
@Query("... + variable + ...")
```

### 🟠 P1 - High

#### 1. N+1 Problem
```java
for (~ : list) {
    repository.find~();
}
```

#### 2. LazyInitializationException
```java
// 트랜잭션 외부에서 Lazy Loading
domain.getRelation().size()
```

---

## 📚 개발자 가이드

### PR 작성자

1. **PR 생성 후 대기** (2-5분)
2. **Bot 리뷰 확인**
   - 🔴 P0/🟠 P1: 반드시 수정
   - 🟡 P2/🟢 P3: 팀 판단
3. **수정 후 재리뷰**: `/gemini review` 댓글

### 리뷰어 (사람)

1. **Bot 리뷰 우선 확인**
2. **비즈니스 로직 검토** (Bot이 놓친 부분)
3. **최종 승인**

### 코드 작성 가이드

상세한 코딩 스타일 및 리뷰 기준은 `code-review-guide.md` 참고:
- Multi-Module Architecture 규칙
- Clean Architecture 계층 흐름
- Naming Convention
- Transaction 관리
- 잠재적 위험 탐지 방법

---

## ⚠️ 주의사항

### API 사용량 제한

- **Consumer**: 일일 33개 PR 제한
- **Enterprise**: 일일 100+ PR 지원

### False Positive 처리

잘못된 리뷰 시:
- `@gemini-code-assist[bot]` 멘션으로 재검토 요청
- 또는 팀 판단으로 무시

---

## 🔍 트러블슈팅

### Bot이 리뷰하지 않음

**원인:**
- GitHub App 미설치
- Repository 권한 부족
- config.yml 오류

**해결:**
```bash
# 1. App 설치 확인
# Settings → Integrations → Applications → Gemini Code Assist

# 2. config.yml 검증
cat .gemini/config.yml

# 3. 권한 확인
# Settings → Collaborators → gemini-code-assist[bot]
```

### 리뷰가 너무 일반적임

**해결:**
- `styleguide.md`에 구체적인 패턴 추가
- `comment_severity_threshold` 조정

### 한국어가 아님

**확인:**
- `styleguide.md`에 언어 요구사항 확인:
  ```markdown
  - **All review comments must be written in Korean (ko-KR)**
  ```

---

## 📈 기대 효과

- **리뷰 시간 단축**: 30-50% 감소
- **버그 조기 발견**: P0/P1 이슈 사전 차단
- **코드 품질 향상**: 컨벤션 준수율 증가
- **생산성 향상**: 사람은 비즈니스 로직에 집중

---

## 📖 참고 문서

- **code-review-guide.md**: 개발자용 상세 코드 리뷰 가이드
  - Spring Boot 코딩 스타일
  - 잠재적 위험 탐지 방법
  - 리뷰 체크리스트

- **External Links**:
  - [Gemini Code Assist - GitHub Marketplace](https://github.com/marketplace/gemini-code-assist)
  - [Review GitHub code using Gemini](https://developers.google.com/gemini-code-assist/docs/review-github-code)
  - [Customize Gemini behavior](https://developers.google.com/gemini-code-assist/docs/customize-gemini-behavior-github)

---

## ✅ 설정 완료 체크리스트

- [ ] GitHub App 설치 완료
- [ ] Repository 권한 부여 완료
- [ ] `.gemini/config.yml` 확인
- [ ] `.gemini/styleguide.md` 확인
- [ ] 테스트 PR 생성 및 리뷰 확인
- [ ] 리뷰 형식이 기대와 일치하는지 확인
- [ ] 팀원들에게 사용법 공유

**모두 완료되면 자동 리뷰가 작동합니다! 🎉**

---

## 📞 문의

문제 발생 시:
1. 이 문서 및 `code-review-guide.md` 확인
2. GitHub Issues 검색
3. 팀 채널 문의

Happy Coding with AI! 🚀
