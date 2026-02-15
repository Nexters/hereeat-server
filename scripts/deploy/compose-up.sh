#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${API_IMAGE_FULL_URL:-}" || -z "${BATCH_IMAGE_FULL_URL:-}" ]]; then
  echo "ERROR: API_IMAGE_FULL_URL and BATCH_IMAGE_FULL_URL must be set"
  exit 1
fi

DOCKERHUB_API_IMAGE_NAME="${DOCKERHUB_API_IMAGE_NAME:-yogieat-server-api}"
DOCKERHUB_BATCH_IMAGE_NAME="${DOCKERHUB_BATCH_IMAGE_NAME:-yogieat-server-batch-sync}"
export API_IMAGE_FULL_URL BATCH_IMAGE_FULL_URL
export DOCKERHUB_API_IMAGE_NAME DOCKERHUB_BATCH_IMAGE_NAME

ENV_FILE_PATH="${ENV_FILE_PATH:-~/.env}"
if [[ ! -f "${ENV_FILE_PATH}" ]]; then
  echo "ERROR: env file not found: ${ENV_FILE_PATH}"
  exit 1
fi
export ENV_FILE_PATH

DEPLOY_SCOPE="${DEPLOY_SCOPE:-app}"
if [[ "${DEPLOY_SCOPE}" != "app" && "${DEPLOY_SCOPE}" != "full" ]]; then
  echo "ERROR: DEPLOY_SCOPE must be one of [app, full]"
  exit 1
fi

COMPOSE_FILES=(-f docker-compose.yaml)

ENABLE_EDGE_SSL="${ENABLE_EDGE_SSL:-false}"
if [[ "${DEPLOY_SCOPE}" == "app" && "${ENABLE_EDGE_SSL}" == "true" ]]; then
  echo "ERROR: ENABLE_EDGE_SSL=true is not allowed when DEPLOY_SCOPE=app"
  echo "       Use existing nginx/letsencrypt in production, or set DEPLOY_SCOPE=full."
  exit 1
fi

if [[ "${ENABLE_EDGE_SSL}" == "true" ]]; then
  if [[ -z "${EDGE_DOMAIN:-}" ]]; then
    echo "ERROR: EDGE_DOMAIN must be set when ENABLE_EDGE_SSL=true"
    exit 1
  fi
  export EDGE_DOMAIN
  COMPOSE_FILES+=(-f docker-compose.edge.yaml)
fi

if [[ "${DEPLOY_SCOPE}" == "app" ]]; then
  DB_CONTAINER_NAME="${DB_CONTAINER_NAME:-yogieat-db}"
  AUTO_RESTORE_DB="${AUTO_RESTORE_DB:-true}"
  DB_READY_TIMEOUT_SECONDS="${DB_READY_TIMEOUT_SECONDS:-60}"

  if ! docker ps --format '{{.Names}}' | grep -xq "${DB_CONTAINER_NAME}"; then
    if [[ "${AUTO_RESTORE_DB}" != "true" ]]; then
      echo "ERROR: required DB container '${DB_CONTAINER_NAME}' is not running."
      echo "       AUTO_RESTORE_DB=false, so deploy is stopped."
      exit 1
    fi

    echo "DB container '${DB_CONTAINER_NAME}' is not running. Attempting auto-restore..."
    docker compose --env-file "${ENV_FILE_PATH}" "${COMPOSE_FILES[@]}" up -d yogieat-db

    STARTED_AT="$(date +%s)"
    while true; do
      if docker ps --format '{{.Names}}' | grep -xq "${DB_CONTAINER_NAME}"; then
        if docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${DB_CONTAINER_NAME}" 2>/dev/null | grep -Eq 'healthy|running'; then
          break
        fi
      fi

      NOW="$(date +%s)"
      if (( NOW - STARTED_AT >= DB_READY_TIMEOUT_SECONDS )); then
        echo "ERROR: DB container '${DB_CONTAINER_NAME}' did not become ready within ${DB_READY_TIMEOUT_SECONDS}s."
        docker ps -a --filter "name=${DB_CONTAINER_NAME}" || true
        docker logs --tail=100 "${DB_CONTAINER_NAME}" || true
        exit 1
      fi
      sleep 2
    done
    echo "DB auto-restore succeeded: ${DB_CONTAINER_NAME}"
  fi
fi

echo "Deploy API image: ${API_IMAGE_FULL_URL}"
echo "Deploy Batch image: ${BATCH_IMAGE_FULL_URL}"
echo "Deploy scope: ${DEPLOY_SCOPE}"
echo "Enable edge SSL: ${ENABLE_EDGE_SSL}"
echo "Env file path: ${ENV_FILE_PATH}"
echo "Target services: yogieat-api yogieat-batch-sync"
if [[ "${DEPLOY_SCOPE}" == "app" ]]; then
  echo "Auto restore DB: ${AUTO_RESTORE_DB:-true}"
fi

if [[ "${DEPLOY_SCOPE}" == "app" ]]; then
  docker compose --env-file "${ENV_FILE_PATH}" "${COMPOSE_FILES[@]}" up -d --no-deps yogieat-api yogieat-batch-sync
else
  docker compose --env-file "${ENV_FILE_PATH}" "${COMPOSE_FILES[@]}" up -d
fi

echo "Deploy completed: scope=${DEPLOY_SCOPE}, edge_ssl=${ENABLE_EDGE_SSL}, env_file=${ENV_FILE_PATH}"
