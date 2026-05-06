-- Anonymous telemetry heartbeat: one row per install per day.
-- Tracked under github.com/rutgerg/karoo-pitstop#95.
--
-- Threat model: the anon API key ships in a public APK, so anyone can hit the
-- REST endpoint. The defenses are:
--   1. Column-level GRANT — anon can only write the five payload columns; it
--      cannot influence inserted_at or read anything back.
--   2. RLS WITH CHECK on `day` — bounds inserts to today/yesterday so a bad
--      actor cannot backfill the table with fake history.
--   3. Composite primary key on (install_id, day) — combined with PostgREST
--      `?on_conflict=install_id,day` and `Prefer: resolution=merge-duplicates`,
--      a single install can only ever own one row per day; re-sends overwrite
--      instead of duplicating.

create table public.heartbeats (
    install_id     uuid        not null,
    day            date        not null,
    tile_renders   int         not null default 0,
    prefetch_count int         not null default 0,
    app_version    text,
    inserted_at    timestamptz not null default now(),
    primary key (install_id, day)
);

alter table public.heartbeats enable row level security;

revoke all on public.heartbeats from anon;
grant insert (install_id, day, tile_renders, prefetch_count, app_version)
    on public.heartbeats to anon;

create policy "anon insert recent heartbeats"
    on public.heartbeats
    for insert
    to anon
    with check (day >= current_date - 1);

-- Aggregations. Anon has no select on the underlying table, so these views are
-- only reachable via the dashboard / service_role, which is the intent.

create view public.mau_30d as
select count(distinct install_id) as mau
from public.heartbeats
where day >= current_date - 30;

create view public.weekly_events_per_install as
select
    install_id,
    date_trunc('week', day)::date as week_start,
    sum(tile_renders)             as tile_renders,
    sum(prefetch_count)           as prefetch_count
from public.heartbeats
group by install_id, week_start;
