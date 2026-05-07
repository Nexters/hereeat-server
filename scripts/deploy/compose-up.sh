#!/usr/bin/env bash
set -euo pipefail

error() {
  echo "ERROR: $*"
  exit 1
}

warn() {
  echo "WARN: $*"
}

APP_SERVICES=("yogieat-api" "yogieat-admin" "yogieat-batch-sync")
SELECTED_APP_SERVICES=()

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

default_container_names() {
  DOCKERHUB_API_IMAGE_NAME="${DOCKERHUB_API_IMAGE_NAME:-yogieat-server-api}"
  DOCKERHUB_ADMIN_IMAGE_NAME="${DOCKERHUB_ADMIN_IMAGE_NAME:-yogieat-server-admin}"
  DOCKERHUB_BATCH_IMAGE_NAME="${DOCKERHUB_BATCH_IMAGE_NAME:-yogieat-server-batch-sync}"
  export DOCKERHUB_API_IMAGE_NAME DOCKERHUB_ADMIN_IMAGE_NAME DOCKERHUB_BATCH_IMAGE_NAME
}

resolve_container_name() {
  local service_name="$1"

  case "${service_name}" in
    yogieat-api)
      echo "${DOCKERHUB_API_IMAGE_NAME}"
      ;;
    yogieat-admin)
      echo "${DOCKERHUB_ADMIN_IMAGE_NAME}"
      ;;
    yogieat-batch-sync)
      echo "${DOCKERHUB_BATCH_IMAGE_NAME}"
      ;;
    *)
      error "unsupported deploy service: ${service_name}"
      ;;
  esac
}

resolve_image_url() {
  local service_name="$1"

  case "${service_name}" in
    yogieat-api)
      echo "${API_IMAGE_FULL_URL:-}"
      ;;
    yogieat-admin)
      echo "${ADMIN_IMAGE_FULL_URL:-}"
      ;;
    yogieat-batch-sync)
      echo "${BATCH_IMAGE_FULL_URL:-}"
      ;;
    *)
      error "unsupported deploy service: ${service_name}"
      ;;
  esac
}

contains_app_service() {
  local candidate="$1"
  local service_name

  for service_name in "${APP_SERVICES[@]}"; do
    if [[ "${service_name}" == "${candidate}" ]]; then
      return 0
    fi
  done
  return 1
}

select_all_app_services() {
  SELECTED_APP_SERVICES=("${APP_SERVICES[@]}")
}

configure_deploy_services() {
  local deploy_services="${DEPLOY_SERVICES:-all}"
  deploy_services="${deploy_services//,/ }"

  if [[ -z "${deploy_services// /}" || "${deploy_services}" == "all" ]]; then
    select_all_app_services
    return 0
  fi

  SELECTED_APP_SERVICES=()
  local service_name
  for service_name in ${deploy_services}; do
    contains_app_service "${service_name}" \
      || error "DEPLOY_SERVICES contains unsupported service '${service_name}'. Allowed: all ${APP_SERVICES[*]}"
    SELECTED_APP_SERVICES+=("${service_name}")
  done

  [[ "${#SELECTED_APP_SERVICES[@]}" -gt 0 ]] \
    || error "DEPLOY_SERVICES resolved to an empty service list."
}

ensure_container_on_network() {
  local container_name="$1"
  local network_name="$2"

  if ! docker network inspect "${network_name}" >/dev/null 2>&1; then
    warn "network '${network_name}' does not exist. creating..."
    docker network create "${network_name}" >/dev/null \
      || error "failed to create network '${network_name}'."
  fi

  if docker inspect --format '{{json .NetworkSettings.Networks}}' "${container_name}" 2>/dev/null \
    | grep -Fq "\"${network_name}\":"; then
    return 0
  fi

  warn "container '${container_name}' is not connected to network '${network_name}'. connecting..."
  docker network connect "${network_name}" "${container_name}" >/dev/null \
    || error "failed to connect container '${container_name}' to network '${network_name}'."
}

validate_required_env() {
  local service_name image_url

  for service_name in "${SELECTED_APP_SERVICES[@]}"; do
    image_url="$(resolve_image_url "${service_name}")"
    [[ -n "${image_url}" ]] || error "image URL env is missing for selected service '${service_name}'"
  done

  export API_IMAGE_FULL_URL ADMIN_IMAGE_FULL_URL BATCH_IMAGE_FULL_URL
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
  COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-yogieat}"
  export COMPOSE_PROJECT_NAME

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
  APP_NETWORK_NAME="${APP_NETWORK_NAME:-yogieat-network}"

  if is_container_running "${DB_CONTAINER_NAME}"; then
    ensure_container_on_network "${DB_CONTAINER_NAME}" "${APP_NETWORK_NAME}"
    echo "DB container '${DB_CONTAINER_NAME}' is already running. Skipping DB reconciliation for app-only deploy."
    return 0
  fi

  if container_exists "${DB_CONTAINER_NAME}"; then
    if [[ "${AUTO_RESTORE_DB}" != "true" ]]; then
      error "required DB container '${DB_CONTAINER_NAME}' exists but is not running.
       AUTO_RESTORE_DB=false, so deploy is stopped."
    fi

    echo "DB container '${DB_CONTAINER_NAME}' exists but is not running. Starting existing container..."
    if ! docker start "${DB_CONTAINER_NAME}"; then
      warn "failed to start existing DB container '${DB_CONTAINER_NAME}'."
      echo "      Recreating DB container with current compose configuration..."
      docker ps -a --filter "name=${DB_CONTAINER_NAME}" || true
      docker logs --tail=100 "${DB_CONTAINER_NAME}" || true
      docker rm "${DB_CONTAINER_NAME}" || error "failed to remove broken DB container '${DB_CONTAINER_NAME}'."
      compose_cmd up -d yogieat-db
    fi
    ensure_container_on_network "${DB_CONTAINER_NAME}" "${APP_NETWORK_NAME}"
    wait_for_db_ready "${DB_CONTAINER_NAME}" "${DB_READY_TIMEOUT_SECONDS}"
    echo "DB start succeeded: ${DB_CONTAINER_NAME}"
    return 0
  fi

  if [[ "${AUTO_RESTORE_DB}" != "true" ]]; then
    error "required DB container '${DB_CONTAINER_NAME}' does not exist.
       AUTO_RESTORE_DB=false, so deploy is stopped."
  fi

  echo "DB container '${DB_CONTAINER_NAME}' does not exist. Attempting auto-restore with compose..."
  compose_cmd up -d yogieat-db
  ensure_container_on_network "${DB_CONTAINER_NAME}" "${APP_NETWORK_NAME}"
  wait_for_db_ready "${DB_CONTAINER_NAME}" "${DB_READY_TIMEOUT_SECONDS}"
  echo "DB auto-restore succeeded: ${DB_CONTAINER_NAME}"
}

cleanup_stale_app_containers() {
  AUTO_CLEANUP_STALE_APP_CONTAINERS="${AUTO_CLEANUP_STALE_APP_CONTAINERS:-true}"
  if [[ "${AUTO_CLEANUP_STALE_APP_CONTAINERS}" != "true" ]]; then
    echo "Skip stale app container cleanup: AUTO_CLEANUP_STALE_APP_CONTAINERS=false"
    return 0
  fi

  local service_name container_name existing_container_id compose_container_id

  for service_name in "${SELECTED_APP_SERVICES[@]}"; do
    container_name="$(resolve_container_name "${service_name}")"

    if ! container_exists "${container_name}"; then
      continue
    fi

    existing_container_id="$(docker inspect --format '{{.Id}}' "${container_name}" 2>/dev/null || true)"
    compose_container_id="$(compose_cmd ps -q "${service_name}" 2>/dev/null || true)"

    if [[ -n "${compose_container_id}" && "${compose_container_id}" == "${existing_container_id}" ]]; then
      continue
    fi

    warn "Removing stale container '${container_name}' (service=${service_name}) to avoid name conflict."
    docker rm -f "${container_name}" >/dev/null \
      || error "failed to remove stale container '${container_name}'."
  done
}

verify_deployed_images() {
  VERIFY_DEPLOYED_IMAGES="${VERIFY_DEPLOYED_IMAGES:-true}"
  if [[ "${VERIFY_DEPLOYED_IMAGES}" != "true" ]]; then
    echo "Skip deployed image verification: VERIFY_DEPLOYED_IMAGES=false"
    return 0
  fi

  local service_name container_name expected_image actual_image

  for service_name in "${SELECTED_APP_SERVICES[@]}"; do
    container_name="$(resolve_container_name "${service_name}")"
    expected_image="$(resolve_image_url "${service_name}")"
    actual_image="$(docker inspect --format '{{.Config.Image}}' "${container_name}")"

    [[ "${actual_image}" = "${expected_image}" ]] \
      || error "${service_name} image mismatch: expected=${expected_image}, actual=${actual_image}"
  done
}

print_deploy_summary() {
  echo "Deploy API image: ${API_IMAGE_FULL_URL}"
  echo "Deploy Admin image: ${ADMIN_IMAGE_FULL_URL}"
  echo "Deploy Batch image: ${BATCH_IMAGE_FULL_URL}"
  echo "Deploy scope: ${DEPLOY_SCOPE}"
  echo "Deploy env: ${DEPLOY_ENV}"
  echo "Enable edge SSL: ${ENABLE_EDGE_SSL}"
  echo "Env file path: ${ENV_FILE_PATH}"
  echo "Compose files: ${COMPOSE_FILES[*]}"
  echo "Target services: ${SELECTED_APP_SERVICES[*]}"
  if [[ "${DEPLOY_SCOPE}" == "app" ]]; then
    echo "Auto restore DB: ${AUTO_RESTORE_DB:-true}"
    echo "Auto cleanup stale app containers: ${AUTO_CLEANUP_STALE_APP_CONTAINERS:-true}"
    echo "App network name: ${APP_NETWORK_NAME:-yogieat-network}"
  fi
  echo "Pull images on deploy: ${PULL_IMAGES_ON_DEPLOY:-true}"
}

main() {
  default_container_names
  configure_deploy_services
  validate_required_env
  resolve_env_file
  configure_scope_and_files
  PULL_IMAGES_ON_DEPLOY="${PULL_IMAGES_ON_DEPLOY:-true}"
  export PULL_IMAGES_ON_DEPLOY

  if [[ "${DEPLOY_SCOPE}" == "app" ]]; then
    ensure_db_running_for_app_scope
    cleanup_stale_app_containers
  fi

  print_deploy_summary

  if [[ "${PULL_IMAGES_ON_DEPLOY}" == "true" ]]; then
    if [[ "${DEPLOY_SCOPE}" == "app" ]]; then
      compose_cmd pull "${SELECTED_APP_SERVICES[@]}"
    else
      compose_cmd pull
    fi
  fi

  if [[ "${DEPLOY_SCOPE}" == "app" ]]; then
    compose_cmd up -d --no-deps "${SELECTED_APP_SERVICES[@]}"
  else
    compose_cmd up -d
  fi

  verify_deployed_images

  echo "Deploy completed: scope=${DEPLOY_SCOPE}, edge_ssl=${ENABLE_EDGE_SSL}, env_file=${ENV_FILE_PATH}"
}

main "$@"
