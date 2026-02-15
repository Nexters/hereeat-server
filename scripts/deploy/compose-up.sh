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

ENV_FILE_PATH="${ENV_FILE_PATH:-../.env}"
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
  if ! docker ps --format '{{.Names}}' | grep -xq 'yogieat-db'; then
    echo "ERROR: required DB container 'yogieat-db' is not running."
    echo "       Start/restore DB container first, then retry app-only deploy."
    exit 1
  fi
fi

echo "Deploy API image: ${API_IMAGE_FULL_URL}"
echo "Deploy Batch image: ${BATCH_IMAGE_FULL_URL}"
echo "Deploy scope: ${DEPLOY_SCOPE}"
echo "Enable edge SSL: ${ENABLE_EDGE_SSL}"
echo "Env file path: ${ENV_FILE_PATH}"
echo "Target services: yogieat-api yogieat-batch-sync"

if [[ "${DEPLOY_SCOPE}" == "app" ]]; then
  docker compose --env-file "${ENV_FILE_PATH}" "${COMPOSE_FILES[@]}" up -d --no-deps yogieat-api yogieat-batch-sync
else
  docker compose --env-file "${ENV_FILE_PATH}" "${COMPOSE_FILES[@]}" up -d
fi

echo "Deploy completed: scope=${DEPLOY_SCOPE}, edge_ssl=${ENABLE_EDGE_SSL}, env_file=${ENV_FILE_PATH}"
