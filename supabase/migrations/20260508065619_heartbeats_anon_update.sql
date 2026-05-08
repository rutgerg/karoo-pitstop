-- Move from direct anon INSERT to a SECURITY DEFINER RPC function for upserts.
-- Tracked under github.com/rutgerg/karoo-pitstop#129.
--
-- Migration 20260506000000 granted anon INSERT only. Once the client started
-- sending Prefer: resolution=merge-duplicates (PR #125) the upsert path
-- triggered INSERT ... ON CONFLICT DO UPDATE which requires UPDATE plus
-- SELECT permission on the conflicting row. Granting both to anon would let
-- a holder of the public anon key read every install's recent counters,
-- which contradicts the docs/privacy.md claim that anon cannot read existing
-- rows.
--
-- This migration removes anon's direct table access entirely and exposes a
-- single SECURITY DEFINER function that performs the upsert internally,
-- bypassing RLS. Anon's only privilege on this schema is `execute` on the
-- function. The function bounds the day window to today/yesterday — same
-- guard the original RLS WITH CHECK enforced — so a leaked anon key still
-- cannot backfill historical rows.

drop policy if exists "anon insert recent heartbeats" on public.heartbeats;
revoke insert (install_id, day, tile_renders, prefetch_count, app_version)
    on public.heartbeats from anon;

create or replace function public.heartbeat_upsert(
    p_install_id uuid,
    p_day date,
    p_tile_renders int,
    p_prefetch_count int,
    p_app_version text
) returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if p_day < current_date - 1 or p_day > current_date then
        raise exception 'day out of allowed window: %', p_day
            using errcode = '42501';
    end if;
    insert into public.heartbeats (install_id, day, tile_renders, prefetch_count, app_version)
        values (p_install_id, p_day, p_tile_renders, p_prefetch_count, p_app_version)
        on conflict (install_id, day) do update
            set tile_renders   = excluded.tile_renders,
                prefetch_count = excluded.prefetch_count,
                app_version    = excluded.app_version;
end;
$$;

revoke all on function public.heartbeat_upsert from public;
grant execute on function public.heartbeat_upsert to anon;
