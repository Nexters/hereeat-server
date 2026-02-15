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

COMPOSE_FILES=(-f docker-compose.yaml)
if [[ "${ENABLE_EDGE_SSL:-false}" == "true" ]]; then
  if [[ -z "${EDGE_DOMAIN:-}" ]]; then
    echo "ERROR: EDGE_DOMAIN must be set when ENABLE_EDGE_SSL=true"
    exit 1
  fi
  export EDGE_DOMAIN
  COMPOSE_FILES+=(-f docker-compose.edge.yaml)
fi

echo "Deploy API image: ${API_IMAGE_FULL_URL}"
echo "Deploy Batch image: ${BATCH_IMAGE_FULL_URL}"
echo "Enable edge SSL: ${ENABLE_EDGE_SSL:-false}"
echo "Env file path: ${ENV_FILE_PATH}"

docker compose --env-file "${ENV_FILE_PATH}" "${COMPOSE_FILES[@]}" up -d
