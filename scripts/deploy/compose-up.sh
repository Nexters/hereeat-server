#!/usr/bin/env bash
set -euo pipefail

error() {
  echo "ERROR: $*"
  exit 1
}

warn() {
  echo "WARN: $*"
}

is_container_running() {
  local name="$1"
  docker ps --format '{{.Names}}' | grep -xq "${name}"
}

container_exists() {
  local name="$1"
  docker ps -a --format '{{.Names}}' | grep -xq "${name}"
}

compose_cmd() {
  docker compose --env-file "${ENV_FILE_PATH}" "${COMPOSE_FILES[@]}" "$@"
}

validate_required_env() {
  [[ -n "${API_IMAGE_FULL_URL:-}" && -n "${BATCH_IMAGE_FULL_URL:-}" ]] \
    || error "API_IMAGE_FULL_URL and BATCH_IMAGE_FULL_URL must be set"

  DOCKERHUB_API_IMAGE_NAME="${DOCKERHUB_API_IMAGE_NAME:-yogieat-server-api}"
  DOCKERHUB_BATCH_IMAGE_NAME="${DOCKERHUB_BATCH_IMAGE_NAME:-yogieat-server-batch-sync}"
  export API_IMAGE_FULL_URL BATCH_IMAGE_FULL_URL
  export DOCKERHUB_API_IMAGE_NAME DOCKERHUB_BATCH_IMAGE_NAME
}

resolve_env_file() {
  ENV_FILE_PATH="${ENV_FILE_PATH:-~/.env}"
  # Expand "~" to HOME so values like "~/.env" work under non-interactive shells too.
  if [[ "${ENV_FILE_PATH}" == "~"* ]]; then
    ENV_FILE_PATH="${HOME}${ENV_FILE_PATH:1}"
  fi

  [[ -f "${ENV_FILE_PATH}" ]] || error "env file not found: ${ENV_FILE_PATH}
       Hint: pass an absolute path (e.g. /root/.env) if needed."

  export ENV_FILE_PATH
}

configure_scope_and_files() {
  DEPLOY_SCOPE="${DEPLOY_SCOPE:-app}"
  [[ "${DEPLOY_SCOPE}" == "app" || "${DEPLOY_SCOPE}" == "full" ]] \
    || error "DEPLOY_SCOPE must be one of [app, full]"

  COMPOSE_FILES=(-f docker-compose.yaml)

  DEPLOY_ENV="${DEPLOY_ENV:-dev}"
  case "${DEPLOY_ENV}" in
    dev)
      COMPOSE_FILES+=(-f docker-compose.dev.yaml)
      ;;
    prod)
      COMPOSE_FILES+=(-f docker-compose.prod.yaml)
      ;;
    *)
      error "DEPLOY_ENV must be one of [dev, prod]"
      ;;
  esac

  ENABLE_EDGE_SSL="${ENABLE_EDGE_SSL:-false}"
  if [[ "${DEPLOY_SCOPE}" == "app" && "${ENABLE_EDGE_SSL}" == "true" ]]; then
    error "ENABLE_EDGE_SSL=true is not allowed when DEPLOY_SCOPE=app
       Use existing nginx/letsencrypt in production, or set DEPLOY_SCOPE=full."
  fi

  if [[ "${ENABLE_EDGE_SSL}" == "true" ]]; then
    [[ -n "${EDGE_DOMAIN:-}" ]] || error "EDGE_DOMAIN must be set when ENABLE_EDGE_SSL=true"
    export EDGE_DOMAIN
    COMPOSE_FILES+=(-f docker-compose.edge.yaml)
  fi
}

wait_for_db_ready() {
  local db_container_name="$1"
  local timeout_seconds="$2"
  local started_at now

  started_at="$(date +%s)"
  while true; do
    if is_container_running "${db_container_name}"; then
      if docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${db_container_name}" 2>/dev/null | grep -Eq 'healthy|running'; then
        return 0
      fi
    fi

    now="$(date +%s)"
    if (( now - started_at >= timeout_seconds )); then
      docker ps -a --filter "name=${db_container_name}" || true
      docker logs --tail=100 "${db_container_name}" || true
      error "DB container '${db_container_name}' did not become ready within ${timeout_seconds}s."
    fi
    sleep 2
  done
}

ensure_db_running_for_app_scope() {
  DB_CONTAINER_NAME="${DB_CONTAINER_NAME:-yogieat-db}"
  AUTO_RESTORE_DB="${AUTO_RESTORE_DB:-true}"
  DB_READY_TIMEOUT_SECONDS="${DB_READY_TIMEOUT_SECONDS:-60}"

  if is_container_running "${DB_CONTAINER_NAME}"; then
    return 0
  fi

  if [[ "${AUTO_RESTORE_DB}" != "true" ]]; then
    error "required DB container '${DB_CONTAINER_NAME}' is not running.
       AUTO_RESTORE_DB=false, so deploy is stopped."
  fi

  if container_exists "${DB_CONTAINER_NAME}"; then
    echo "DB container '${DB_CONTAINER_NAME}' exists but is not running. Starting existing container..."
    if ! docker start "${DB_CONTAINER_NAME}"; then
      warn "failed to start existing DB container '${DB_CONTAINER_NAME}'."
      echo "      Recreating DB container with current compose configuration..."
      docker ps -a --filter "name=${DB_CONTAINER_NAME}" || true
      docker logs --tail=100 "${DB_CONTAINER_NAME}" || true
      docker rm "${DB_CONTAINER_NAME}" || error "failed to remove broken DB container '${DB_CONTAINER_NAME}'."
      compose_cmd up -d yogieat-db
    fi
  else
    echo "DB container '${DB_CONTAINER_NAME}' does not exist. Attempting auto-restore with compose..."
    compose_cmd up -d yogieat-db
  fi

  wait_for_db_ready "${DB_CONTAINER_NAME}" "${DB_READY_TIMEOUT_SECONDS}"
  echo "DB auto-restore succeeded: ${DB_CONTAINER_NAME}"
}

print_deploy_summary() {
  echo "Deploy API image: ${API_IMAGE_FULL_URL}"
  echo "Deploy Batch image: ${BATCH_IMAGE_FULL_URL}"
  echo "Deploy scope: ${DEPLOY_SCOPE}"
  echo "Deploy env: ${DEPLOY_ENV}"
  echo "Enable edge SSL: ${ENABLE_EDGE_SSL}"
  echo "Env file path: ${ENV_FILE_PATH}"
  echo "Compose files: ${COMPOSE_FILES[*]}"
  echo "Target services: yogieat-api yogieat-batch-sync"
  if [[ "${DEPLOY_SCOPE}" == "app" ]]; then
    echo "Auto restore DB: ${AUTO_RESTORE_DB:-true}"
  fi
}

main() {
  validate_required_env
  resolve_env_file
  configure_scope_and_files

  if [[ "${DEPLOY_SCOPE}" == "app" ]]; then
    ensure_db_running_for_app_scope
  fi

  print_deploy_summary

  if [[ "${DEPLOY_SCOPE}" == "app" ]]; then
    compose_cmd up -d --no-deps yogieat-api yogieat-batch-sync
  else
    compose_cmd up -d
  fi

  echo "Deploy completed: scope=${DEPLOY_SCOPE}, edge_ssl=${ENABLE_EDGE_SSL}, env_file=${ENV_FILE_PATH}"
}

main "$@"
