#!/usr/bin/env bash
set -euo pipefail

ALL_SERVICES=("api" "admin" "batch")
SELECT_API=false
SELECT_ADMIN=false
SELECT_BATCH=false
HAS_DEPLOYABLE_CHANGE=false

compose_service_for() {
  local service="$1"

  case "${service}" in
    api)
      echo "yogieat-api"
      ;;
    admin)
      echo "yogieat-admin"
      ;;
    batch)
      echo "yogieat-batch-sync"
      ;;
    *)
      echo "unsupported service: ${service}" >&2
      return 1
      ;;
  esac
}

jib_task_for() {
  local service="$1"

  case "${service}" in
    api)
      echo ":apps:api:jib"
      ;;
    admin)
      echo ":apps:admin:jib"
      ;;
    batch)
      echo ":batch:sync:jib"
      ;;
    *)
      echo "unsupported service: ${service}" >&2
      return 1
      ;;
  esac
}

is_selected() {
  local service="$1"

  case "${service}" in
    api)
      [[ "${SELECT_API}" == "true" ]]
      ;;
    admin)
      [[ "${SELECT_ADMIN}" == "true" ]]
      ;;
    batch)
      [[ "${SELECT_BATCH}" == "true" ]]
      ;;
    *)
      return 1
      ;;
  esac
}

select_service() {
  local service="$1"

  case "${service}" in
    api)
      SELECT_API=true
      ;;
    admin)
      SELECT_ADMIN=true
      ;;
    batch)
      SELECT_BATCH=true
      ;;
    *)
      echo "unsupported service: ${service}" >&2
      return 1
      ;;
  esac

  HAS_DEPLOYABLE_CHANGE=true
}

select_all_services() {
  local service
  for service in "${ALL_SERVICES[@]}"; do
    select_service "${service}"
  done
}

is_docs_only_path() {
  local path="$1"

  case "${path}" in
    README.md|docs/*|.github/ISSUE_TEMPLATE/*|.github/PULL_REQUEST_TEMPLATE.md|.github/CODEOWNERS)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

resolve_path() {
  local path="$1"

  [[ -n "${path}" ]] || return 0

  case "${path}" in
    apps/api/*|support/swagger/*|support/monitoring/*)
      select_service "api"
      ;;
    apps/admin/*)
      select_service "admin"
      ;;
    batch/sync/*)
      select_service "batch"
      ;;
    apps/domain/*|storage/db-core/*|external/*|support/logging/*|gradle/*|build.gradle|settings.gradle|gradle.properties|gradlew|gradlew.bat)
      select_all_services
      ;;
    docker/*|scripts/deploy/*)
      select_all_services
      ;;
    .github/workflows/*|scripts/ci/*|AGENTS.md)
      select_all_services
      ;;
    *)
      if ! is_docs_only_path "${path}"; then
        select_all_services
      fi
      ;;
  esac
}

join_by_space() {
  local first=true
  local item

  for item in "$@"; do
    if [[ "${first}" == "true" ]]; then
      printf "%s" "${item}"
      first=false
    else
      printf " %s" "${item}"
    fi
  done
}

emit_output() {
  local services=()
  local compose_services=()
  local jib_tasks=()
  local service

  for service in "${ALL_SERVICES[@]}"; do
    if is_selected "${service}"; then
      services+=("${service}")
      compose_services+=("$(compose_service_for "${service}")")
      jib_tasks+=("$(jib_task_for "${service}")")
    fi
  done

  local skip_deploy="false"
  if [[ "${HAS_DEPLOYABLE_CHANGE}" == "false" ]]; then
    skip_deploy="true"
  fi

  local services_value compose_services_value jib_tasks_value
  services_value=""
  compose_services_value=""
  jib_tasks_value=""
  if ((${#services[@]} > 0)); then
    services_value="$(join_by_space "${services[@]}")"
    compose_services_value="$(join_by_space "${compose_services[@]}")"
    jib_tasks_value="$(join_by_space "${jib_tasks[@]}")"
  fi

  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    {
      echo "skip_deploy=${skip_deploy}"
      echo "services=${services_value}"
      echo "deploy_services=${compose_services_value}"
      echo "jib_tasks=${jib_tasks_value}"
    } >> "${GITHUB_OUTPUT}"
  else
    echo "skip_deploy=${skip_deploy}"
    echo "services=${services_value}"
    echo "deploy_services=${compose_services_value}"
    echo "jib_tasks=${jib_tasks_value}"
  fi
}

if (($# > 0)); then
  for path in "$@"; do
    resolve_path "${path}"
  done
else
  while IFS= read -r path; do
    resolve_path "${path}"
  done
fi

emit_output
