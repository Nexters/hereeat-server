# API + Admin + Batch 인스턴스 배포/운영 가이드

## 1. 결론 요약
- `batch:sync`는 API와 **별도 애플리케이션 프로세스**로 실행해야 한다.
- 운영 배포는 `DEPLOY_SCOPE=app` 기준으로 **API/Admin/BATCH만 재배포**한다.
- `yogieat-db`는 기존 컨테이너를 유지하며 배포 과정에서 생성/재시작/삭제하지 않는다.
- Batch는 외부 라우팅 대상이 아니므로 포트를 열지 않는다.
- 운영 SSL 종료는 인스턴스의 `nginx + letsencrypt`를 사용하며, 배포에서 edge(Caddy) 경로는 비활성화한다.

## 2. 런타임 토폴로지

### 운영(권장)
1. Client -> 443 -> `nginx + letsencrypt`
2. `nginx` path 라우팅
   - `/api/v1/admin/*` -> `127.0.0.1:8081` (Docker Admin 컨테이너)
   - 그 외 `/api/*` -> `127.0.0.1:8080` (Docker API 컨테이너)
4. `yogieat-batch-sync` -> `yogieat-db` (내부 네트워크)
5. `yogieat-api` -> `yogieat-db` (내부 네트워크)
6. `yogieat-admin` -> `yogieat-db` (내부 네트워크)

#### nginx location 예시
```nginx
location /api/v1/admin/ {
    proxy_pass http://127.0.0.1:8081;
}

location /api/ {
    proxy_pass http://127.0.0.1:8080;
}
```

## 3. 적용된 파일
- 기본 Compose: `docker/docker-compose.yaml`
- DEV 리소스 오버레이: `docker/docker-compose.dev.yaml`
- PROD 리소스 오버레이: `docker/docker-compose.prod.yaml`
- 인스턴스 수동 배포 스크립트: `scripts/deploy/compose-up.sh`
- CI/CD 워크플로우
  - `.github/workflows/develop_build_deploy.yml`
  - `.github/workflows/production_build_deploy.yml`
  - `.github/workflows/production_deploy.yml`

## 4. 이미지/배포 전략

### 이미지 분리
- API 이미지: `yogieat/yogieat-server-api:<tag>`
- Admin 이미지: `yogieat/yogieat-server-admin:<tag>`
- Batch 이미지: `yogieat/yogieat-server-batch-sync:<tag>`

Dockerfile은 동일하고 `JAR_FILE` build-arg만 다르게 사용한다.
- API: `apps/api/build/libs/*.jar`
- Admin: `apps/admin/build/libs/*.jar`
- Batch: `batch/sync/build/libs/*.jar`

### CI/CD 동작
1. Gradle build
2. API/Admin/BATCH 이미지 각각 build & push
3. 서버로 `docker/` 디렉터리 및 `scripts/deploy/` rsync
4. 서버에서 `DEPLOY_SCOPE=app`, `DEPLOY_ENV=(dev|prod)`로 `scripts/deploy/compose-up.sh` 실행

## 5. 인스턴스 실행 방법

### 5.1 운영 표준 실행 (APP ONLY)
서버에서 `~/docker` 기준:

```bash
export API_IMAGE_FULL_URL=yogieat/yogieat-server-api:<tag>
export ADMIN_IMAGE_FULL_URL=yogieat/yogieat-server-admin:<tag>
export BATCH_IMAGE_FULL_URL=yogieat/yogieat-server-batch-sync:<tag>
export DOCKERHUB_API_IMAGE_NAME=yogieat-server-api
export DOCKERHUB_ADMIN_IMAGE_NAME=yogieat-server-admin
export DOCKERHUB_BATCH_IMAGE_NAME=yogieat-server-batch-sync
export API_HOST_PORT=8080
export ADMIN_HOST_PORT=8081
export BATCH_SERVER_PORT=9090

cd ~/docker
API_IMAGE_FULL_URL=yogieat/yogieat-server-api:<tag> \
ADMIN_IMAGE_FULL_URL=yogieat/yogieat-server-admin:<tag> \
BATCH_IMAGE_FULL_URL=yogieat/yogieat-server-batch-sync:<tag> \
DOCKERHUB_API_IMAGE_NAME=yogieat-server-api \
DOCKERHUB_ADMIN_IMAGE_NAME=yogieat-server-admin \
DOCKERHUB_BATCH_IMAGE_NAME=yogieat-server-batch-sync \
API_HOST_PORT=8080 \
ADMIN_HOST_PORT=8081 \
BATCH_SERVER_PORT=9090 \
DEPLOY_SCOPE=app \
DEPLOY_ENV=dev \
ENV_FILE_PATH=~/.env \
../scripts/deploy/compose-up.sh
```

`DEPLOY_SCOPE=app`일 때는 내부적으로 아래와 같이 동작한다.
- `docker compose ... up -d --no-deps yogieat-api yogieat-admin yogieat-batch-sync`
- 즉, DB 컨테이너는 배포에서 제외된다.
- 단, `yogieat-db`가 없으면 배포 스크립트가 `docker compose ... up -d yogieat-db`를 먼저 실행해 자동 복구한다.
- `DEPLOY_ENV=dev`면 `docker-compose.dev.yaml`, `DEPLOY_ENV=prod`면 `docker-compose.prod.yaml`를 추가 적용한다.

### 5.2 환경별 리소스 제한
- DEV (`docker/docker-compose.dev.yaml`)
  - 서버 스펙 목표: `1 vCPU / 1GB`
  - `yogieat-api`: `cpus=0.55`, `mem_limit=320m`, `mem_reservation=160m`
  - `yogieat-admin`: `cpus=0.15`, `mem_limit=160m`, `mem_reservation=80m`
  - `yogieat-batch-sync`: `cpus=0.10`, `mem_limit=128m`, `mem_reservation=48m`
  - `yogieat-db`: `cpus=0.20`, `mem_limit=192m`, `mem_reservation=96m`
- PROD (`docker/docker-compose.prod.yaml`)
  - 서버 스펙 목표: `2 vCPU / 4GB`
  - `yogieat-api`: `cpus=1.05`, `mem_limit=1536m`, `mem_reservation=768m`
  - `yogieat-admin`: `cpus=0.25`, `mem_limit=512m`, `mem_reservation=192m`
  - `yogieat-batch-sync`: `cpus=0.15`, `mem_limit=384m`, `mem_reservation=128m`
  - `yogieat-db`: `cpus=0.45`, `mem_limit=1024m`, `mem_reservation=512m`

`DEPLOY_SCOPE=app` 배포는 API/Admin/BATCH 중심으로 동작하며, DB는 필요 시 자동 복구(기동/재생성)된다.
DB 설정을 강제로 재적용하려면 유지보수 창에 `DEPLOY_SCOPE=full` 배포를 사용한다.

## 6. 라우팅/SSL 설계 결정 포인트

### 라우팅
- 외부 진입점은 API만 허용
- Admin은 별도 포트(기본 8081)를 통해 인스턴스 내부 프록시(nginx)에서 라우팅한다.
- Batch는 외부 요청을 받지 않음
- API/Admin/Batch/DB는 동일 bridge network(`yogieat-network`) 사용

### SSL
- 운영은 인스턴스의 `nginx + letsencrypt`로 TLS 종료한다.
- 운영 배포에서 `ENABLE_EDGE_SSL=true`는 사용하지 않는다.
- `DEPLOY_SCOPE=app`에서 `ENABLE_EDGE_SSL=true`를 주면 스크립트가 실패하도록 보호 로직이 있다.
- 자동 DB 복구를 끄려면 `AUTO_RESTORE_DB=false`를 전달한다.

## 7. 보안 권장사항
1. Security Group
- 22: 운영자 IP만 허용
- 80/443: 전체 허용(nginx/letsencrypt 사용)
- 8080: 외부 차단(로컬 바인딩 권장)
- 8081: 외부 차단(로컬 바인딩 권장)
- 5432: 외부 차단

2. 환경변수/비밀
- `.env`로 DB 계정/암호 주입
- `.env`에 Admin JWT 서명키 주입 (`JWT_SECRET`, 최소 32자)
- Docker Hub 토큰은 GitHub Secrets로 관리

## 8. 운영 체크리스트
1. 컨테이너 상태
```bash
docker ps
```
2. 로그 확인
```bash
docker logs -f yogieat-server-api

docker logs -f yogieat-server-admin

docker logs -f yogieat-server-batch-sync
```
3. 배치 동작 확인
- `t_restaurant_sync_job`에서 `PENDING -> RUNNING -> SUCCESS/PARTIAL_FAILED` 전이 확인
4. 성능 회귀 점검
- `docs/operations/restaurant-sync-performance-checklist.md` 기준으로 전/후 비교 수행

## 9. 롤백
- 이전 태그로 `API_IMAGE_FULL_URL`, `ADMIN_IMAGE_FULL_URL`, `BATCH_IMAGE_FULL_URL` 재지정 후 compose up 재실행
- 데이터 스키마 변경이 수반되지 않는 한 애플리케이션 레벨 롤백 가능

## 10. 장애 대응
- 증상: `Conflict. The container name "/yogieat-db" is already in use`
  - 원인: DB가 이미 떠 있는데 배포가 DB까지 다시 `up`하려 할 때 발생
  - 대응:
    1. `DEPLOY_SCOPE=app`으로 배포 실행
    2. `docker ps --format '{{.Names}}' | grep -x yogieat-db`로 DB 실행 확인
    3. `ENABLE_EDGE_SSL`은 비활성(`false`) 유지

- 증상: 인스턴스 재시작 후 DB 컨테이너가 없어져 앱 배포 실패
  - 대응:
    1. 기본값(`AUTO_RESTORE_DB=true`)으로 푸시 배포를 재실행
    2. 스크립트가 DB 컨테이너를 자동으로 복구 기동
    3. 필요 시 `DB_READY_TIMEOUT_SECONDS`로 대기시간 조정 (기본 60초)

## 11. 주의사항
- 이 저장소 변경은 배포 아티팩트를 준비한 것이며, 실제 서버 반영은 CI 실행 또는 서버에서 compose 명령 실행이 필요하다.
- 운영은 nginx/letsencrypt 기준으로 관리한다. Caddy edge는 운영 기본 경로가 아니다.
