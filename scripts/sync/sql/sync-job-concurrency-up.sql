-- Sync Job concurrency safety migration (PostgreSQL)
-- 1) Query pattern indexes
create index if not exists idx_sync_job_scope_status_created_at
    on t_restaurant_sync_job (scope, status, created_at);

create index if not exists idx_sync_job_target_status_created_at
    on t_restaurant_sync_job (target_restaurant_id, status, created_at);

-- 2) Duplicate prevention constraints for active jobs
-- ALL scope: only one RUNNING/PENDING job
create unique index if not exists uq_sync_job_all_active
    on t_restaurant_sync_job (scope)
    where scope = 'ALL' and status in ('PENDING', 'RUNNING');

-- SINGLE scope: only one RUNNING/PENDING job per target restaurant
create unique index if not exists uq_sync_job_single_target_active
    on t_restaurant_sync_job (target_restaurant_id)
    where scope = 'SINGLE'
      and status in ('PENDING', 'RUNNING')
      and target_restaurant_id is not null;
