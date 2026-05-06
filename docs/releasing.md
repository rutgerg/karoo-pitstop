# Releasing (maintainers)

Prebuilt APKs are produced by `.github/workflows/release.yml` on every `v*` tag push and attached to the corresponding GitHub release. `versionName`, `versionCode`, and the `KarooExtension` reported version are all derived from the most recent `v*` git tag — no source edits needed before tagging:

```bash
git tag -a vX.Y.Z -m "..."
git push origin vX.Y.Z
```

The workflow requires a `KAROO_EXT_PAT` repository secret — a PAT with `read:packages` scope — so the CI runner can fetch `karoo-ext` from GitHub Packages. Add it under Settings → Secrets and variables → Actions. The workflow can also be triggered manually from the Actions tab via **Run workflow** (useful for back-filling the APK on an existing release).

## Release checklist

Before tagging a new `vX.Y.Z`:

- [ ] **Hardware test (only for non-mechanical releases)** — sideload the latest debug build to a Karoo 3, plan a route, confirm the 9 tiles render with live data and that tapping a tile opens the pin activity. Mechanical releases (version-only bumps) can skip this.
- [ ] **Screenshot** — refresh `docs/screenshot-karoo-tiles.png` if the tile layout, font, or content changed.
- [ ] **Status line** — if you re-tested on hardware, append a "last confirmed YYYY-MM-DD" date to the Status section's `Verified end-to-end on a Karoo 3` line.

## Telemetry Supabase project

Anonymous heartbeat telemetry (#95) writes to a single Supabase project. The credentials live outside the repo:

- `SUPABASE_URL` and `SUPABASE_ANON_KEY` belong in `~/.gradle/gradle.properties` (or exported as env vars of the same name). The Android build reads them via `BuildConfig`. When unset, the build sees empty strings and the heartbeat sender no-ops — the rest of the app is unaffected.
- The canonical maintainer copy of the URL and anon key lives in your private password manager / secret note. Update that note when rotating the key, then update the `KAROO_EXT_PAT`-equivalent repo secret so the release workflow can build APKs with telemetry baked in.
- The schema is reproducible from `supabase/migrations/20260506000000_heartbeats.sql`. On a fresh project: `supabase db push` (CLI) or paste the file into the SQL editor.
- Aggregation views (`mau_30d`, `weekly_events_per_install`) are read from the Supabase dashboard. They are not exposed via the public REST API because the `anon` role has no `select` on `heartbeats`.
