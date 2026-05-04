# karoo-restaurant

An Android app for the Hammerhead Karoo 3 that surfaces the **nearest open restaurant, supermarket, and fuel station** along a planned route, and routes you there in two taps via the Karoo's built-in navigation.

<p align="center">
  <img src="docs/screenshot-karoo-tiles.png" alt="Three nearby-POI data tiles on a Karoo 3 ride profile page" width="280" />
</p>

## Status

- **`:app`** — Android module. Three per-category data field tiles (Restaurant / Supermarket / Fuel) registered as `KarooExtension` data types render on a ride profile page. Tap a tile to dispatch `LaunchPinDrop` and open the Karoo's pin activity. Verified end-to-end on a Karoo 3 (v0.1.0).
- **`:data`** — Headless Kotlin/JVM module: Overpass POI fetcher, opening-hours evaluator, polyline decoder, SQLite cache. Runnable on a Mac via `./gradlew :data:run`. JUnit tests cover slicer, opening-hours, polyline. App-level tests cover `KarooClient.navigateTo` and `RouteWatcher`.

## How it works

1. You plan a route on the Karoo's native navigator.
2. `RouteWatcher` (Application-scoped) sees the route appear, samples the polyline every 2 km, queries Overpass with a 10 km buffer, and upserts ~thousands of POIs into local SQLite. Dedups on a `route_fetches` table so the same route isn't re-fetched.
3. Three data field tiles (Restaurant / Supermarket / Fuel) on your ride profile page render the nearest non-closed POI per category, with distance, name, and the OSM `opening_hours` line. Closed entries are filtered out; Open and Unknown are both shown.
4. Tap a tile → `LaunchPinDrop(Symbol.POI(...))` opens the Karoo's pin Activity → tap **Navigate** (replaces the active route) or **Save as POI** (bookmarks for later — no route change).

## Project shape

```
karoo_restaurant/
├── app/                                              — Android module
│   └── src/main/kotlin/dev/karoorestaurant/
│       ├── KarooRestaurantApp.kt                     Application; assembles KarooClient + RouteWatcher
│       ├── KarooClient.kt                            wraps the system port, exposes locationFlow + routeFlow + navigateTo
│       ├── KarooSystemPort.kt                        port over KarooSystemService; production + fake share the interface
│       ├── RouteWatcher.kt                           collects routeFlow, prefetches corridor, exposes RouteFetchState
│       ├── RestaurantExtensionService.kt             KarooExtension service; registers the three DataTypeImpl tiles
│       ├── NearbyPoiDataType.kt                      per-category data tile rendering distance + name + opening hours
│       ├── NearbyPicks.kt                            shared compute for tile + Activity picker
│       ├── LaunchPoiReceiver.kt                      tile-tap broadcast → KarooClient.navigateTo
│       ├── TestLocationReceiver.kt                   debug-only: inject a synthetic GPS location
│       ├── SeedPoisReceiver.kt                       debug-only: populate the cache around a coordinate
│       ├── MainActivity.kt                           Compose picker (legacy entry point; kept for emulator runs)
│       ├── PoiNearby.kt                              UI display model
│       ├── db/{PoiStore,AndroidPoiStore}.kt          interface + SQLiteOpenHelper impl
│       └── ui/{PoiCard, Theme}.kt
│   └── src/main/res/
│       ├── drawable/{ic_restaurant,ic_supermarket,ic_fuel}.xml
│       ├── layout/data_field_nearby_poi.xml         RemoteViews layout for the data tile
│       └── xml/extension_info.xml                    extension metadata read by the Karoo system
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

After install + reboot, the Karoo App Store binds the extension and its three data types appear in the Karoo Pages data-type picker. Add **Restaurant**, **Supermarket**, and **Fuel** to a ride profile page; that page is the on-device entry point. There is no app-drawer icon — see *Karoo platform constraints* below.

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

## Karoo platform constraints

Things discovered building this extension that are not obvious from the `karoo-ext` 1.1.8 docs. Recorded here so future-you (or anyone forking this) does not re-derive them on a frustrating afternoon.

### Entry points and surfaces
- **There is no third-party app drawer on the Karoo 3.** The home screen is the Hammerhead launcher and does not enumerate activities with `LAUNCHER` intent filters. A traditional Android-style "tap an icon to launch the app" path does not exist for sideloaded apps.
- **`KarooExtension` is an Android `Service`, not an Activity wrapper.** Registering one does not give your app a tappable icon anywhere. The only on-device surfaces it exposes are: data field tiles (`DataTypeImpl`), map overlays (`MapEffect`), bonus actions on paired controllers (`BonusAction`), and FIT-file effects.
- **`BonusAction` requires a paired hardware controller.** It surfaces only as an assignable function on a SRAM AXS-style remote (Blip / Eagle / similar). Without one paired, `BonusAction` has no UI to bind to.

### Service lifecycle
- **The Karoo App Store starts extensions as foreground services on `BOOT_COMPLETED`.** Your `KarooExtension` subclass *must* call `startForeground(notificationId, notification)` in `onCreate()` within ~5 seconds. If it doesn't, Android ANRs the service and the App Store removes it from the registered extensions list. Symptom: your data types never appear in the Karoo Pages data-type picker and `adb logcat` shows `Context.startForegroundService() did not then call Service.startForeground()` for your package.
- **`android:foregroundServiceType` is required on `targetSdk` 34+.** Use `dataSync` plus `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />` to keep the manifest valid.
- **The Karoo system logs extension events under tag `HHApp: Extensions:`**, not under the karoo-ext SDK's `KarooExtension` tag. Filter logcat with `grep -i HHApp` if you are looking for bind/connect/disconnect events.

### Connectivity
- **Internet access requires a tethered phone** (Bluetooth or WiFi). The Karoo 3 has no SIM. Network calls fail when the phone is out of range. Prefetch corridor data while tethered, then serve from local SQLite for the rest of the ride.

### Routing
- **There is no public "add waypoint" or "insert stop" effect** in `karoo-ext` 1.1.8. The closest available effect is `LaunchPinDrop`, which opens the Karoo's pin activity giving the user **Navigate to** (replaces the active route) or **Save as POI** (bookmarks). Inserting a stop into an existing route without destroying it is not exposed to third-party extensions.

### SDK distribution
- **`karoo-ext` 1.1.8 is published only to GitHub Packages**, with mandatory PAT (`read:packages` scope) auth even for public reads. No JitPack or Maven Central mirror.

### Tile rendering
- **Data tiles use `RemoteViews`**, so layouts are limited to the subset Android exposes for cross-process inflation (`FrameLayout`, `LinearLayout`, `TextView`, `ImageView`, etc.). The Karoo Pages app does honor `setOnClickPendingIntent` on the root view (verified on hardware), so tiles can be made tappable.
- **Hardcoded white text** on tiles is fine in practice — Karoo Pages renders data fields on dark backgrounds. There is no public theme attribute exposed to extensions to follow user theme choice.

### Debug-broadcast quirks
- **`am broadcast` on Karoo Android does not accept `--ed` (double extras).** Use `--es` (string) and parse to double in the receiver.
- **Manifest receivers do not get implicit broadcasts in the background** (Android 8+). Scope test broadcasts to your package with `-p <pkg>` to make them explicit; otherwise they are silently dropped.
- **`adb install -r` puts the app in stopped state.** Pass `--include-stopped-packages` to subsequent `am broadcast` calls or wake the service first via `am start-foreground-service -n <pkg>/.<Service>`.

## What's next (post-v0.1.0)

The v0.2 backlog covers: app-icon polish, chunked Overpass queries for routes >80 km, settings screen (radius, category priority), additional categories (bar, cafe, pharmacy), cache TTL with "unverified" badge for older entries, small-screen layout audit, save-favorites, and a ground-truth ride checkpoint. See the [v0.2 project board](https://github.com/users/rutgerg/projects/14) for tracked issues.
