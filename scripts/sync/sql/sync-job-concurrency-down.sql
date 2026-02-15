-- Rollback for sync-job-concurrency-up.sql
drop index if exists uq_sync_job_single_target_active;
drop index if exists uq_sync_job_all_active;
drop index if exists idx_sync_job_target_status_created_at;
drop index if exists idx_sync_job_scope_status_created_at;
