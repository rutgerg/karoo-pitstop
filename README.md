# karoo-restaurant

An Android app for the Hammerhead Karoo 3 that surfaces the **nearest open restaurant, supermarket, and fuel station** along a planned route, and routes you there in two taps via the Karoo's built-in navigation.

## Status

- **`:app`** — Android module, route-driven UI implemented. Compose screen with four states (idle / fetching / cached / error). Validated end-to-end on a Pixel emulator. On-device verification still pending.
- **`:data`** — Headless Kotlin/JVM module: Overpass POI fetcher, opening-hours evaluator, polyline decoder, SQLite cache. Runnable on a Mac via `./gradlew :data:run`. JUnit tests cover slicer, opening-hours, polyline.

## How it works

1. You plan a route on the Karoo's native navigator.
2. `RouteWatcher` (Application-scoped) sees the route appear, samples the polyline every 2 km, queries Overpass with a 10 km buffer, and upserts ~thousands of POIs into local SQLite. Dedups on a `route_fetches` table so the same route isn't re-fetched.
3. Mid-ride you open Restaurant. The screen shows the nearest non-closed POI per category from your current GPS position. Cards are labeled `open` or `hours unknown` per the project UX rule (Closed entries are filtered out).
4. Tap a card → `LaunchPinDrop(Symbol.POI(...))` opens the Karoo's pin Activity → tap **Navigate** to start turn-by-turn.

## Project shape

```
karoo_restaurant/
├── app/                                              — Android module
│   └── src/main/kotlin/dev/karoorestaurant/
│       ├── KarooRestaurantApp.kt                     Application; owns singleton KarooClient + RouteWatcher
│       ├── KarooClient.kt                            wraps KarooSystemService, exposes locationFlow + routeFlow
│       ├── RouteWatcher.kt                           collects routeFlow, prefetches corridor, exposes RouteFetchState
│       ├── MainActivity.kt                           Compose screen for the four route-fetch states
│       ├── PoiNearby.kt                              UI display model
│       ├── db/AndroidPoiStore.kt                     SQLiteOpenHelper, pois + route_fetches tables
│       └── ui/{PoiCard, Theme}.kt
├── data/                                             — Kotlin/JVM module
│   └── src/main/kotlin/dev/karoorestaurant/data/
│       ├── Main.kt                                   CLI runner
│       ├── route/{LatLng, Geo, CorridorSlicer, Polyline, Route}.kt
│       ├── poi/{Poi, PoiCategory, OpeningHours}.kt
│       ├── overpass/{OverpassClient, OverpassResponse}.kt
│       └── store/PoiStore.kt                         JDBC SQLite for the headless prototype
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/wrapper/gradle-wrapper.properties
```

The Android app uses the consumer-side `KarooSystemService` from a regular Activity — no `KarooExtension` subclass.

## Prerequisites

- Android Studio Koala (or newer) — manages the JDK, gradle wrapper, AGP.
- A Karoo 3 with USB-debugging enabled: Settings → System → About → tap build number 7× → enable USB debugging in Developer options.
- A GitHub Personal Access Token with the `read:packages` scope. The `karoo-ext` SDK is published only to GitHub Packages, which **requires authentication even for public reads**.

## Setup

1. Create a PAT at github.com/settings/tokens → **Generate new token (classic)** → scope **`read:packages`** only.
2. Add credentials to `~/.gradle/gradle.properties` (NOT to the repo):

   ```properties
   gpr.user=YOUR_GITHUB_USERNAME
   gpr.key=ghp_xxxxxxxxxxxxxxxxxxxx
   ```

   Or export `GITHUB_USERNAME` / `GITHUB_TOKEN` in your shell.
3. Open the project folder in Android Studio. Let it sync gradle. First sync downloads `karoo-ext` from GitHub Packages — if it 401s, your PAT is wrong or missing the `read:packages` scope.

## Build & sideload to Karoo

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then launch **Restaurant** from the Karoo app drawer (swipe down → All apps).

For a quicker turnaround during development, run from Studio: select the **app** run config, plug in the Karoo, click ▶︎.

## Run on the Pixel emulator

The app installs and launches on a stock Pixel 7 emulator. Without a Karoo OS the SDK can't bind, so `routeFlow` and `locationFlow` never emit — the UI sits on the Idle state ("Plan a route on the Karoo to load nearby POIs"). Useful for verifying the Idle state renders cleanly; not useful for the live cycle.

## Run the data prototype (no Karoo needed)

```bash
./gradlew :data:run
```

Resamples a hard-coded Amsterdam → Haarlem → IJmuiden loop, fetches POIs from Overpass, persists to `pois.sqlite`, prints per-category counts, the nearest non-closed pick per category, and the `opening_hours` coverage rate. The same data layer is wired into `:app` via the `Polyline`, `OverpassClient`, and `OpeningHours` types.

CLI flags (all optional):

- `--db=path.sqlite` — DB file, default `pois.sqlite`
- `--step=2000` — resampling step in metres
- `--radius=10000` — Overpass `around:` radius in metres
- `--refetch` — force a fresh fetch even if the DB is non-empty

Tests: `./gradlew :data:test`.

## Troubleshooting

**`401 Unauthorized` when resolving `io.hammerhead:karoo-ext`** — the PAT in `~/.gradle/gradle.properties` is missing the `read:packages` scope, or you edited a different token than the one in the file. Verify with:

```bash
TOKEN=$(grep '^gpr.key=' ~/.gradle/gradle.properties | cut -d= -f2-)
curl -sI -u "rutgerg:$TOKEN" https://api.github.com/user | grep -i x-oauth-scopes
```

You should see `read:packages` in the listed scopes.

**App opens on the Karoo but stays on the empty state** — no route is currently loaded. The watcher only fires on `OnNavigationState.NavigatingRoute` / `NavigatingToDestination`. Plan a route first, then re-open the app.

**Cards show "Waiting for GPS location…"** — the Karoo's GPS hasn't acquired a fix yet. Take it outside or wait. On the Pixel emulator this is permanent because there's no Karoo OS to emit `OnLocationChanged`.

**Fetch fails with `Couldn't cache POIs`** — usually the tethered phone isn't reachable. Verify the Karoo says "Connected" in the phone-connection settings. Also check Overpass status (`https://overpass-api.de/api/status`) for rate-limit hits.

**`stableIds.txt: Operation not permitted` or "file located outside root directory"** — gradle has a stale path cache. Run `./gradlew clean`, delete any stale `.idea/` at old project locations, then **File → Invalidate Caches and Restart** in Studio.

## Known constraints (from `karoo-ext` 1.1.8)

- The Karoo's home/launcher screen is not extensible by third parties — this is an Activity in the app drawer, not a homescreen tile.
- Internet access on the Karoo 3 requires a tethered phone (Bluetooth or WiFi); no SIM. Routes are prefetched while tethered, then served from the local SQLite cache for the rest of the ride.
- `karoo-ext` 1.1.8 is published only to GitHub Packages with mandatory PAT auth. No JitPack / Maven Central mirror.

## What's next (post-v0.1.0)

The v0.2 backlog covers: app-icon polish, chunked Overpass queries for routes >80 km, settings screen (radius, category priority), additional categories (bar, cafe, pharmacy), cache TTL with "unverified" badge for older entries, small-screen layout audit, save-favorites, and a ground-truth ride checkpoint. See the [v0.2 project board](https://github.com/users/rutgerg/projects/14) for tracked issues.
