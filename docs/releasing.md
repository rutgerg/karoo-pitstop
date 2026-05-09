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
- [ ] **Release notes** — add a `## X.Y.Z` section at the top of `docs/release-notes.md` with user-facing bullets. The workflow extracts this section into the Karoo App Manifest `releaseNotes` field shown in the on-device About panel.

## Telemetry Supabase project

Anonymous heartbeat telemetry (#95) writes to a single Supabase project. The credentials live outside the repo:

- The build reads `supabase.url` and `supabase.anonKey` from `~/.gradle/gradle.properties` (NOT committed) and exposes them as `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY`. When either is empty (e.g. on a fresh clone before you set them) the heartbeat sender no-ops — the rest of the app is unaffected. A third flag, `BuildConfig.TELEMETRY_ENABLED`, is hardcoded `false` until #98 wires it to a Settings toggle and flips the default to `true` (opt-out semantics).

### Privacy and data handling (store-listing copy)

Reusable in the Hammerhead store-submission email and equivalent disclosures. Update both this paragraph and the README Privacy section together — one source of truth, two surfaces:

> Pitstop fetches points of interest from the public OpenStreetMap Overpass API over Wi-Fi when a route is planned. No personal data is sent for this fetch — only the route corridor coordinates, which are not retained server-side. Pitstop also collects anonymous usage telemetry by default: a per-day count of tile renders and route prefetches, tagged with a randomly generated install ID. No location, no POI names, no route data, no IP retained. This can be turned off at any time in Settings → Telemetry. The full data-handling statement, including all fields, retention, and the database schema, is in [`docs/privacy.md`](privacy.md).
- The canonical maintainer copy of the URL and anon key lives in your private password manager / secret note. Update that note when rotating the key, then update the `KAROO_EXT_PAT`-equivalent repo secret so the release workflow can build APKs with telemetry baked in.
- The schema is reproducible from `supabase/migrations/20260506000000_heartbeats.sql`. On a fresh project: `supabase db push` (CLI) or paste the file into the SQL editor.
- Aggregation views (`mau_30d`, `weekly_events_per_install`) are read from the Supabase dashboard. They are not exposed via the public REST API because the `anon` role has no `select` on `heartbeats`.
