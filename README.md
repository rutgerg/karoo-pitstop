# Pitstop

An Android app for the Hammerhead Karoo 3 that surfaces the **nearest open restaurant, supermarket, and fuel station** along a planned route, and routes you there in two taps via the Karoo's built-in navigation.

Repository: [github.com/rutgerg/karoo-pitstop](https://github.com/rutgerg/karoo-pitstop)

[![Latest release](https://img.shields.io/github/v/release/rutgerg/karoo-pitstop)](https://github.com/rutgerg/karoo-pitstop/releases/latest)
[![Release build](https://img.shields.io/github/actions/workflow/status/rutgerg/karoo-pitstop/release.yml?label=release%20build)](https://github.com/rutgerg/karoo-pitstop/actions/workflows/release.yml)
[![License](https://img.shields.io/github/license/rutgerg/karoo-pitstop)](LICENSE)

<p align="center">
  <img src="docs/screenshot-karoo-tiles.png" alt="Three nearby-POI data tiles on a Karoo 3 ride profile page" width="280" />
</p>

## Why Pitstop

Long rides need food, water, fuel, sometimes a cash machine — and the rider does not want to fumble with a phone in a jersey pocket while moving. The Karoo handles the route, but not the question every cyclist has on hour four:

- **What is open near me right now, along my route?** Stock Karoo navigation routes you, but does not surface live services.
- **Komoot and RideWithGPS POIs are pre-trip planning artefacts.** They show what *exists*, not what is *currently open* — and they sit on a phone you cannot see while pedalling.
- **The bar-mounted screen is the right interaction surface mid-ride.** A glance at a data tile beats fishing out gloved-hand-unfriendly hardware.

## Use cases

Pitstop is built for situations where the route is set but the next stop is undecided:

- **Brevet / audax** — find the next open supermarket between controls without stopping to phone-check.
- **Remote gravel** — locate a fuel station with a shop for water and snacks, often the only thing open for kilometres.
- **Bonk recovery** — when you are crashing, the data tile shows the nearest open food source by distance, no menu diving.
- **Unfamiliar territory** — the Karoo already has the route; let the device tell you which services line it.

## Status

- **`:app`** — Android module. Nine per-category data field tiles (Restaurant, Supermarket, Fuel, Cafe, Hotel, Doctor, Pharmacy, Bike Shop, ATM) registered as `KarooExtension` data types render on a ride profile page. Tap a tile to dispatch `LaunchPinDrop` and open the Karoo's pin activity. Verified end-to-end on a Karoo 3.
- **`:data`** — Headless Kotlin/JVM module: Overpass POI fetcher, opening-hours evaluator, polyline decoder, SQLite cache. Runnable on a Mac via `./gradlew :data:run`. JUnit tests cover slicer, opening-hours, polyline. App-level tests cover `KarooClient.navigateTo` and `RouteWatcher`.

## How it works

1. You plan a route on the Karoo's native navigator.
2. `RouteWatcher` (Application-scoped) sees the route appear, samples the polyline every 2 km, queries Overpass with a 10 km buffer, and upserts ~thousands of POIs into local SQLite. Dedups on a `route_fetches` table so the same route isn't re-fetched.
3. Nine data field tiles (Restaurant, Supermarket, Fuel, Cafe, Hotel, Doctor, Pharmacy, Bike Shop, ATM) on your ride profile page render the nearest non-closed POI per category, with distance, name, and the OSM `opening_hours` line. Closed entries are filtered out; Open and Unknown are both shown.
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
│       ├── RestaurantExtensionService.kt             KarooExtension service; registers the nine DataTypeImpl tiles
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
│       ├── drawable/{ic_restaurant,ic_supermarket,ic_fuel,ic_cafe,ic_hotel,ic_doctor,ic_pharmacy,ic_bike_shop,ic_atm,ic_pitstop}.xml
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

## Install

1. Download `pitstop.apk` from the [latest release](https://github.com/rutgerg/karoo-pitstop/releases/latest).
2. Enable USB debugging on the Karoo 3: Settings → System → About → tap build number 7× → enable USB debugging in Developer options.
3. Sideload over USB:

   ```bash
   adb install pitstop.apk
   ```

4. Reboot the Karoo. The App Store binds the extension as **Pitstop** and the nine data types appear in the Karoo Pages data-type picker. Add any combination of **Restaurant**, **Supermarket**, **Fuel**, **Cafe**, **Hotel**, **Doctor**, **Pharmacy**, **Bike Shop**, and **ATM** tiles to a ride profile page; that page is the on-device entry point. There is no app-drawer icon — see *Karoo platform constraints* below.

## Build from source

Only needed to modify Pitstop or run it on the Pixel emulator. End users should install the prebuilt APK above.

### Prerequisites

- Android Studio Koala (or newer) — manages the JDK, gradle wrapper, AGP.
- A GitHub Personal Access Token with the `read:packages` scope. The `karoo-ext` SDK is published only to GitHub Packages, which **requires authentication even for public reads**.

### Setup

1. Create a PAT at github.com/settings/tokens → **Generate new token (classic)** → scope **`read:packages`** only.
2. Add credentials to `~/.gradle/gradle.properties` (NOT to the repo):

   ```properties
   gpr.user=YOUR_GITHUB_USERNAME
   gpr.key=ghp_xxxxxxxxxxxxxxxxxxxx
   ```

   Or export `GITHUB_USERNAME` / `GITHUB_TOKEN` in your shell.
3. Open the project folder in Android Studio. Let it sync gradle. First sync downloads `karoo-ext` from GitHub Packages — if it 401s, your PAT is wrong or missing the `read:packages` scope.
4. (Optional, recommended) Install the gitleaks pre-commit hook so staged changes are scanned for secrets before every commit:

   ```bash
   brew install pre-commit gitleaks
   pre-commit install
   ```

   Config lives in `.pre-commit-config.yaml` at the repo root.

### Build & sideload

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For a quicker turnaround during development, run from Studio: select the **app** run config, plug in the Karoo, click ▶︎.

### Releasing (maintainers)

Prebuilt APKs are produced by `.github/workflows/release.yml` on every `v*` tag push and attached to the corresponding GitHub release. `versionName`, `versionCode`, and the `KarooExtension` reported version are all derived from the most recent `v*` git tag — no source edits needed before tagging:

```bash
git tag -a vX.Y.Z -m "..."
git push origin vX.Y.Z
```

The workflow requires a `KAROO_EXT_PAT` repository secret — a PAT with `read:packages` scope — so the CI runner can fetch `karoo-ext` from GitHub Packages. Add it under Settings → Secrets and variables → Actions. The workflow can also be triggered manually from the Actions tab via **Run workflow** (useful for back-filling the APK on an existing release).

#### Release checklist

Before tagging a new `vX.Y.Z`:

- [ ] **Hardware test (only for non-mechanical releases)** — sideload the latest debug build to a Karoo 3, plan a route, confirm the 9 tiles render with live data and that tapping a tile opens the pin activity. Mechanical releases (version-only bumps) can skip this.
- [ ] **Screenshot** — refresh `docs/screenshot-karoo-tiles.png` if the tile layout, font, or content changed.
- [ ] **Status line** — if you re-tested on hardware, append a "last confirmed YYYY-MM-DD" date to the Status section's `Verified end-to-end on a Karoo 3` line.

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

**Fetch fails with `Couldn't cache POIs`** — the Karoo doesn't have a Wi-Fi connection. Pitstop calls Overpass over OkHttp on whichever Wi-Fi network the device is currently joined to (home Wi-Fi while charging, or a phone hotspot during a ride). Verify Wi-Fi is connected (Settings → Wi-Fi). Also check Overpass status (`https://overpass-api.de/api/status`) for rate-limit hits.

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
- **The Karoo 3 has Wi-Fi but no SIM, and Pitstop does not use the karoo-ext HTTP bridge.** The watch reaches the internet directly over OkHttp on whatever Wi-Fi network it is joined to — home Wi-Fi at the charging dock, or a phone hotspot during a ride. When no Wi-Fi is available, fetches fail silently and tiles render from the local SQLite cache.
- **The karoo-ext `OnHttpResponse` bridge exists but is unsuitable for bulk POI fetches.** It relays HTTP through a paired phone's BT companion app and enforces a 100 KB request/response cap (`OnHttpResponse.MAX_REQUEST_SIZE` in `karoo-ext-1.1.8`). A typical city-density single-category Overpass response exceeds that cap, so Pitstop bypasses the bridge entirely. The bridge is designed for in-ride micro-API calls (Strava live segments, weather widgets), not bulk prefetch.
- **The README's earlier claim that direct OkHttp cannot reach the internet on a Karoo without a SIM is wrong** when the device is on Wi-Fi. The kernel `main` routing table is empty (which is misleading from a `ip route` shell), but Android uses per-network route tables addressed by the active `Network`. Any app holding `INTERNET` permission routes via the active Wi-Fi network just like any other Android app.

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

## What's next

See the [issue tracker](https://github.com/rutgerg/karoo-pitstop/issues) for open features and bugs.

Hit a bug or have a feature request? [Open an issue](https://github.com/rutgerg/karoo-pitstop/issues/new) — contributions and reports are welcome.

## License

Released under the [MIT License](LICENSE) — © 2026 Rutger Geelen. Free to use, fork, and modify with attribution.
