#!/bin/bash

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT"

collect_changed_files() {
    {
        git diff --name-only --cached --diff-filter=ACMR || true
        git diff --name-only --diff-filter=ACMR HEAD -- || true
        git ls-files --others --exclude-standard || true
    } | sed '/^$/d' | sort -u
}

append_unique_module() {
    local candidate="$1"

    if [[ -z "$candidate" ]]; then
        return
    fi

    if printf '%s\n' "$modules" | grep -Fxq "$candidate"; then
        return
    fi

    if [[ -z "$modules" ]]; then
        modules="$candidate"
        return
    fi

    modules="${modules}
${candidate}"
}

module_for_path() {
    case "$1" in
        apps/api/*) echo ":apps:api" ;;
        apps/admin/*) echo ":apps:admin" ;;
        apps/domain/*) echo ":apps:domain" ;;
        batch/sync/*) echo ":batch:sync" ;;
        external/ai/*) echo ":external:ai" ;;
        external/kakao/*) echo ":external:kakao" ;;
        storage/db-core/*) echo ":storage:db-core" ;;
        support/logging/*) echo ":support:logging" ;;
        support/monitoring/*) echo ":support:monitoring" ;;
        support/swagger/*) echo ":support:swagger" ;;
        *) echo "" ;;
    esac
}

is_runtime_change() {
    case "$1" in
        apps/*|batch/*|external/*|storage/*|support/*|build.gradle|settings.gradle|gradle.properties)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

is_public_api_change() {
    case "$1" in
        apps/api/src/main/*|apps/admin/src/main/*)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

is_shared_contract_change() {
    case "$1" in
        apps/domain/src/main/java/com/yogieat/common/*|\
apps/domain/src/main/java/com/yogieat/*/domain/*|\
apps/domain/src/main/java/com/yogieat/*/result/*|\
apps/domain/src/main/java/com/yogieat/*/service/*Command*|\
apps/domain/src/main/java/com/yogieat/*/service/*Criteria*|\
apps/domain/src/main/java/com/yogieat/*/service/*Repository.java)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

is_shared_test_infra_change() {
    case "$1" in
        apps/*/src/test/java/com/yogieat/DatabaseCleaner.java|\
apps/*/src/test/java/com/yogieat/testsupport/*|\
apps/*/src/test/resources/*)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

echo "==> Running Yogieat repo-local verification"
./gradlew spotlessApply --daemon -q
./gradlew compileJava --daemon -q

modules=""
full_test_reason=""
runtime_change_detected=false

while IFS= read -r path; do
    if [[ -z "$path" ]]; then
        continue
    fi

    if is_runtime_change "$path"; then
        runtime_change_detected=true
    fi

    module="$(module_for_path "$path")"
    append_unique_module "$module"

    if [[ "$path" == storage/* ]]; then
        full_test_reason="storage change"
    fi

    if [[ -z "$full_test_reason" ]] && is_public_api_change "$path"; then
        full_test_reason="public API main code change"
    fi

    if [[ -z "$full_test_reason" ]] && is_shared_contract_change "$path"; then
        full_test_reason="shared contract change"
    fi

    if [[ -z "$full_test_reason" ]] && is_shared_test_infra_change "$path"; then
        full_test_reason="shared test infrastructure change"
    fi

    if [[ -z "$full_test_reason" ]] && [[ "$path" == "build.gradle" || "$path" == "settings.gradle" || "$path" == "gradle.properties" ]]; then
        full_test_reason="root build configuration change"
    fi
done < <(collect_changed_files)

module_count="$(printf '%s\n' "$modules" | sed '/^$/d' | wc -l | tr -d ' ')"

if [[ -z "$full_test_reason" && "$module_count" -gt 1 ]]; then
    full_test_reason="mixed multi-module runtime change"
fi

if [[ -n "$full_test_reason" ]]; then
    echo "==> Escalating to full test: $full_test_reason"
    ./gradlew test --daemon
    exit 0
fi

if [[ "$runtime_change_detected" == false ]]; then
    echo "==> No runtime module changes detected. Skipping Gradle tests."
    exit 0
fi

if [[ "$module_count" -eq 0 ]]; then
    echo "==> No module-local test target detected. Escalating to full test."
    ./gradlew test --daemon
    exit 0
fi

test_tasks=()
while IFS= read -r module; do
    if [[ -z "$module" ]]; then
        continue
    fi
    test_tasks+=("${module}:test")
done < <(printf '%s\n' "$modules" | sed '/^$/d')

echo "==> Running module-local tests: ${test_tasks[*]}"
./gradlew --daemon "${test_tasks[@]}"
