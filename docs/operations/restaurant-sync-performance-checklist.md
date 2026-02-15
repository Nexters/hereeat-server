# Restaurant Sync 성능 검증 체크리스트

## 1. 목적
- Restaurant Sync DB 최적화(`IN` 조회 + JDBC batch update) 적용 전/후 성능을 동일 조건으로 비교한다.
- 목표:
  - `t_restaurant` 대상 `SELECT` 횟수 50% 이상 감소
  - 전체 Job 처리시간 20% 이상 단축
  - 실패율 증가 없음

## 2. 사전 조건
- 비교 대상 커밋 2개 준비
  - Baseline: 최적화 이전
  - Candidate: 최적화 이후
- 동일 데이터셋 사용
- 동일 실행 파라미터 사용
  - `sync.job.chunk-size=50`
  - `sync.job.parallelism=4`

## 3. 측정 절차
1. Baseline 배포 후 batch 로그 수집
```bash
docker logs yogieat-server-batch-sync > baseline-sync.log 2>&1
```

2. 수동 전체 동기화 Job 실행
```bash
curl -X POST http://localhost:9090/api/v1/restaurants/sync-jobs/all
```

3. Job 완료 후 로그 분석
```bash
./scripts/measure/restaurant-sync-sql-metrics.sh baseline-sync.log
```

4. Candidate(최적화 버전)로 동일 절차 반복
```bash
docker logs yogieat-server-batch-sync > candidate-sync.log 2>&1
./scripts/measure/restaurant-sync-sql-metrics.sh candidate-sync.log
```

## 4. 체크 포인트
- SQL 지표
  - `SELECT ... from t_restaurant` 횟수
  - `UPDATE t_restaurant` 횟수
  - 총 SQL 로그 라인 수
- 처리시간 지표
  - Job 시작/종료 시각
  - 총 소요 시간(ms 또는 sec)
- 품질 지표
  - Job 상태(`SUCCESS`, `PARTIAL_FAILED`, `FAILED`)
  - 성공/실패 건수

## 5. 합격 기준
1. `SELECT t_restaurant` 횟수: Baseline 대비 50% 이상 감소
2. 전체 처리시간: Baseline 대비 20% 이상 단축
3. 실패율: Baseline 대비 증가 없음

## 6. 결과 기록 템플릿
```text
[Baseline]
SELECT t_restaurant:
UPDATE t_restaurant:
TOTAL SQL LINES:
ELAPSED:
STATUS:
SUCCESS/FAILED:

[Candidate]
SELECT t_restaurant:
UPDATE t_restaurant:
TOTAL SQL LINES:
ELAPSED:
STATUS:
SUCCESS/FAILED:

[Conclusion]
SELECT 감소율:
처리시간 단축률:
도입 판단: PASS | FAIL
```
