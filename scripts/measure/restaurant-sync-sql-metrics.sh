#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <log-file>"
  exit 1
fi

LOG_FILE="$1"
if [[ ! -f "$LOG_FILE" ]]; then
  echo "ERROR: log file not found: $LOG_FILE"
  exit 1
fi

total_sql_lines=$(rg -i -c "Hibernate:|\\bselect\\b|\\bupdate\\b" "$LOG_FILE" || true)
select_restaurant=$(rg -i -c "select .* from t_restaurant\\b" "$LOG_FILE" || true)
update_restaurant=$(rg -i -c "update t_restaurant\\b" "$LOG_FILE" || true)
select_sync_job=$(rg -i -c "select .* from t_restaurant_sync_job\\b" "$LOG_FILE" || true)
update_sync_job=$(rg -i -c "update t_restaurant_sync_job\\b" "$LOG_FILE" || true)

start_time=$(rg -n "Job started|RUNNING" "$LOG_FILE" | head -n 1 || true)
end_time=$(rg -n "Job finished|SUCCESS|PARTIAL_FAILED|FAILED" "$LOG_FILE" | tail -n 1 || true)

echo "== Restaurant Sync SQL Metrics =="
echo "log_file: $LOG_FILE"
echo "total_sql_lines: $total_sql_lines"
echo "select_t_restaurant: $select_restaurant"
echo "update_t_restaurant: $update_restaurant"
echo "select_t_restaurant_sync_job: $select_sync_job"
echo "update_t_restaurant_sync_job: $update_sync_job"
echo "start_marker: ${start_time:-N/A}"
echo "end_marker: ${end_time:-N/A}"
