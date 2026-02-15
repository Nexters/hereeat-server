# API + Batch 인스턴스 배포/운영 가이드

## 1. 결론 요약
- `batch:sync`는 API와 **별도 애플리케이션 프로세스**로 실행해야 한다.
- 현재 배포 구조는 Docker Compose 기준으로 아래 3개 컨테이너를 띄운다.
  - `yogieat-api` (외부 요청 처리)
  - `yogieat-batch-sync` (스케줄/배치 처리)
  - `yogieat-db` (PostgreSQL + PostGIS)
- Batch는 외부 라우팅 대상이 아니므로 포트를 열지 않는다.

## 2. 런타임 토폴로지

### 기본(비SSL)
1. Client -> Instance:8080 -> `yogieat-api`
2. `yogieat-batch-sync` -> `yogieat-db` (내부 네트워크)
3. `yogieat-api` -> `yogieat-db` (내부 네트워크)

### SSL(권장)
1. Client -> 443 -> Edge(Reverse Proxy)
2. Edge -> `yogieat-api:8080`
3. Batch는 내부 네트워크만 사용(외부 비노출)

## 3. 적용된 파일
- 기본 Compose: `docker/docker-compose.yaml`
- SSL Edge 오버레이: `docker/docker-compose.edge.yaml`
- Caddy 라우팅 설정: `docker/edge/Caddyfile`
- 인스턴스 수동 배포 스크립트: `scripts/deploy/compose-up.sh`
- CI/CD 워크플로우
  - `.github/workflows/develop_build_deploy.yml`
  - `.github/workflows/production_build_deploy.yml`
  - `.github/workflows/production_deploy.yml`

## 4. 이미지/배포 전략

### 이미지 분리
- API 이미지: `yogieat/yogieat-server-api:<tag>`
- Batch 이미지: `yogieat/yogieat-server-batch-sync:<tag>`

Dockerfile은 동일하고 `JAR_FILE` build-arg만 다르게 사용한다.
- API: `apps/api/build/libs/*.jar`
- Batch: `batch/sync/build/libs/*.jar`

### CI/CD 동작
1. Gradle build
2. API/BATCH 이미지 각각 build & push
3. 서버로 `docker/` 디렉터리 및 `scripts/deploy/` rsync
4. 서버에서 `scripts/deploy/compose-up.sh` 실행

## 5. 인스턴스 실행 방법

### 5.1 기본 실행 (비SSL)
서버에서 `~/docker` 기준:

```bash
export API_IMAGE_FULL_URL=yogieat/yogieat-server-api:<tag>
export BATCH_IMAGE_FULL_URL=yogieat/yogieat-server-batch-sync:<tag>
export DOCKERHUB_API_IMAGE_NAME=yogieat-server-api
export DOCKERHUB_BATCH_IMAGE_NAME=yogieat-server-batch-sync

docker compose -f docker-compose.yaml up -d
```

또는 스크립트 사용:

```bash
cd ~/docker
API_IMAGE_FULL_URL=yogieat/yogieat-server-api:<tag> \
BATCH_IMAGE_FULL_URL=yogieat/yogieat-server-batch-sync:<tag> \
DOCKERHUB_API_IMAGE_NAME=yogieat-server-api \
DOCKERHUB_BATCH_IMAGE_NAME=yogieat-server-batch-sync \
ENV_FILE_PATH=../.env \
../scripts/deploy/compose-up.sh
```

### 5.2 SSL 실행 (Caddy Edge)

```bash
cd ~/docker
API_IMAGE_FULL_URL=yogieat/yogieat-server-api:<tag> \
BATCH_IMAGE_FULL_URL=yogieat/yogieat-server-batch-sync:<tag> \
DOCKERHUB_API_IMAGE_NAME=yogieat-server-api \
DOCKERHUB_BATCH_IMAGE_NAME=yogieat-server-batch-sync \
ENABLE_EDGE_SSL=true \
EDGE_DOMAIN=api.example.com \
ENV_FILE_PATH=../.env \
../scripts/deploy/compose-up.sh
```

실행 시 사용 compose:
- `docker-compose.yaml`
- `docker-compose.edge.yaml`

## 6. 라우팅/SSL 설계 결정 포인트

### 라우팅
- 외부 진입점은 API만 허용
- Batch는 외부 요청을 받지 않음
- API/Batch/DB는 동일 bridge network(`yogieat-network`) 사용

### SSL
아래 중 하나를 권장한다.
1. 인스턴스 내부 Edge(Caddy/Nginx)에서 TLS 종료
2. 클라우드 LB(ALB/NLB+TLS)에서 TLS 종료 후 API로 프록시

현재 저장소에는 1번(Caddy 오버레이)을 바로 사용할 수 있게 반영했다.

## 7. 보안 권장사항
1. Security Group
- 22: 운영자 IP만 허용
- 80/443: 전체 허용(Edge 사용 시)
- 8080: 외부 차단 (Edge 사용 시 필수)
- 5432: 외부 차단

2. 환경변수/비밀
- `.env`로 DB 계정/암호 주입
- Docker Hub 토큰은 GitHub Secrets로 관리

## 8. 운영 체크리스트
1. 컨테이너 상태
```bash
docker ps
```
2. 로그 확인
```bash
docker logs -f yogieat-server-api

docker logs -f yogieat-server-batch-sync
```
3. 배치 동작 확인
- `t_restaurant_sync_job`에서 `PENDING -> RUNNING -> SUCCESS/PARTIAL_FAILED` 전이 확인
4. 성능 회귀 점검
- `docs/operations/restaurant-sync-performance-checklist.md` 기준으로 전/후 비교 수행

## 9. 롤백
- 이전 태그로 `API_IMAGE_FULL_URL`, `BATCH_IMAGE_FULL_URL` 재지정 후 compose up 재실행
- 데이터 스키마 변경이 수반되지 않는 한 애플리케이션 레벨 롤백 가능

## 10. 주의사항
- 이 저장소 변경은 배포 아티팩트를 준비한 것이며, 실제 서버 반영은 CI 실행 또는 서버에서 compose 명령 실행이 필요하다.
- 운영 환경에서 Edge SSL을 사용할 경우 DNS가 인스턴스 IP를 가리켜야 하며 80/443 포트가 열려 있어야 한다.
