# Privacy

Pitstop sends a small amount of anonymous usage data on each ride day so the maintainer (one person, this side project) can tell whether anyone is actually using the app and how often. **This is on by default. To turn it off, go to Settings → Telemetry.**

## What gets sent

- A randomly generated install ID (UUID v4, created on first launch — not tied to any account, device, or hardware identifier; resets if the app is uninstalled)
- The current day in UTC
- Two counters from that day: how many times a Pitstop tile was rendered, and how many route prefetches succeeded
- The app version

## What is NOT sent

- Your location, ever — no GPS coordinates, no city, no country
- POI names, OSM IDs, or anything about which specific places you saw or tapped
- Your route, polyline, or any route metadata
- Your IP address (Supabase request logs are not retained beyond standard aggregation)
- Any device identifier, advertising ID, account info, or hardware ID

## Where it goes

A single `heartbeats` table in a Supabase Postgres database managed by the maintainer. Anonymous-only access (insert-only RLS — the public anon key cannot read existing rows). Schema and RLS policies are in [`supabase/migrations/`](../supabase/migrations/) for review.

## How to turn it off

Settings → Telemetry → switch off. The setting persists across rides and reboots. Once off, no further heartbeats are sent for that install.

## Retention

No retention policy is set yet. When usage grows past trivial, expect a periodic rollup of raw rows into weekly aggregates. The data has no PII to retain regardless.
