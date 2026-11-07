-- 1. Create the Supabase table
-- This allows the Android app’s authenticated user to read only their own snapshots.
-- The sync script uses a server-only (service_role) key to write them.

create table if not exists public.student_portal_snapshots (
  owner_id uuid not null references auth.users(id) on delete cascade,
  snapshot_date date not null,
  fetched_at timestamptz not null,
  data jsonb not null,
  primary key (owner_id, snapshot_date)
);

alter table public.student_portal_snapshots enable row level security;

revoke all on table public.student_portal_snapshots from anon, authenticated;
grant select on table public.student_portal_snapshots to authenticated;
grant all on table public.student_portal_snapshots to service_role;

drop policy if exists "Read own SRM snapshots"
  on public.student_portal_snapshots;

create policy "Read own SRM snapshots"
  on public.student_portal_snapshots
  for select
  to authenticated
  using ((select auth.uid()) = owner_id);

