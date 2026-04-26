# karoo-restaurant

An Android app for the Hammerhead Karoo 3 that surfaces the **nearest open restaurant, supermarket, and fuel station** to your current location, and routes you there in two taps via the Karoo's built-in navigation.

## Status

- **`:app`** — Android scaffold. Three placeholder cards render; tap is wired to a `KarooClient` stub. Not yet built/installed on a device.
- **`:data`** — Headless Kotlin/JVM module that proves the data pipeline (Overpass fetch, opening_hours filter, SQLite cache) without a Karoo. Runnable on Mac via `./gradlew :data:run`.

## Project shape

```
karoo_restaurant/
├── app/                                     — Android module (Karoo target)
│   └── src/main/kotlin/dev/karoorestaurant/
│       ├── MainActivity.kt                  three-card screen
│       ├── KarooClient.kt                   wraps KarooSystemService (stub)
│       ├── poi/                             stubs; real impl lives in :data
│       └── ui/                              PoiCard, Theme
├── data/                                    — Kotlin/JVM module (headless prototype)
│   └── src/main/kotlin/dev/karoorestaurant/data/
│       ├── Main.kt                          CLI entry — fetch + cache + query
│       ├── route/{LatLng, CorridorSlicer}.kt
│       ├── poi/{Poi, PoiCategory, OpeningHours}.kt
│       ├── overpass/{OverpassClient, OverpassResponse}.kt
│       └── store/PoiStore.kt                JDBC SQLite
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/wrapper/gradle-wrapper.properties
```

The Android app uses the consumer-side `KarooSystemService` from a regular Activity — no `KarooExtension` subclass.

## Prerequisites

- Android Studio Koala (or newer) — manages the JDK, gradle wrapper, AGP.
- A Karoo 3 with USB-debugging enabled (Settings → System → About → tap build number 7×, then enable USB debugging in Developer options).
- A GitHub Personal Access Token with the `read:packages` scope. The `karoo-ext` SDK is published only to GitHub Packages, which **requires authentication even for public reads**.

## Setup

1. Create a PAT: github.com/settings/tokens → Fine-grained or classic, scope `read:packages`.
2. Add credentials to `~/.gradle/gradle.properties` (NOT to the repo):

   ```properties
   gpr.user=YOUR_GITHUB_USERNAME
   gpr.key=ghp_xxxxxxxxxxxxxxxxxxxx
   ```

   Or export `GITHUB_USERNAME` / `GITHUB_TOKEN` in your shell.

3. Open the `karoo_restaurant` folder in Android Studio. It will detect the missing `gradle-wrapper.jar` and offer to generate it — accept. Alternatively run `gradle wrapper` once if you have a system gradle.
4. Sync gradle. First sync downloads `karoo-ext` from GitHub Packages — if this fails, your PAT is wrong or missing the `read:packages` scope.

## Build & sideload

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then launch "Restaurant" from the Karoo app drawer (swipe down → All apps).

## Run the data prototype (no Karoo needed)

```bash
./gradlew :data:run
```

This:
1. Resamples a hard-coded Amsterdam→Haarlem→IJmuiden→Amsterdam loop (~80 km) to one point per 2 km.
2. POSTs a single Overpass query covering all sample points within a 10 km buffer.
3. Maps results to `Poi` records, dedupes, upserts into `pois.sqlite`.
4. Queries the local DB for the nearest open POI in each category, evaluating `opening_hours` against `LocalDateTime.now()`.
5. Prints totals, per-category counts, the nearest-open pick per category, and the `opening_hours` tag coverage rate.

CLI flags (all optional):
- `--db=path.sqlite` — DB file, default `pois.sqlite`
- `--step=2000` — resampling step in metres
- `--radius=10000` — Overpass `around:` radius in metres

Tests: `./gradlew :data:test`.

The data module **never imports Android** — it's a pure JVM library plus a `main()`. Once verified, the Android app will depend on `:data` directly.

### What this proves
- Overpass returns real, parseable data for your riding region.
- `opening_hours` coverage rate is high enough to be useful (the printed percentage is the load-bearing metric — if it's < 30%, the whole feature concept is in trouble).
- SQLite cache layout works for the bbox+haversine "nearest open" query pattern.
- Custom opening_hours evaluator handles the common 80% (24/7, weekday + time-span lists, multi-rule overrides). PH, date ranges, sunset/sunrise, week numbers fall through to `Unknown`.

## What works right now in :app

- App launches from drawer.
- Three placeholder cards render: Café Luz / Albert Heijn / Shell.
- Tap on a card calls `KarooClient.navigateTo(poi)` which logs to logcat (`adb logcat -s KarooClient`). No actual navigation yet.

## Next milestones

1. **Verify :app on a Karoo 3** — sideload the scaffold and confirm install + launch + tap-logs round-trip.
2. **Wire :data into :app** — replace the in-app stubs with module dependency on `:data`; replace placeholders with live SQLite query.
3. **Route-load watcher** — bind `KarooSystemService`, subscribe to navigation-state, prefetch corridor on route load.
4. **Real navigation dispatch** — `KarooClient.navigateTo` dispatches `LaunchPinDrop(Symbol.POI(...))` to open the Karoo's pin Activity; user confirms with one more tap.

## Known constraints (from `karoo-ext` 1.1.8)

- The Karoo's home/launcher screen is not extensible by third parties — this is an Activity in the app drawer, not a homescreen tile.
- Internet access on the Karoo 3 requires a tethered phone (Bluetooth or WiFi); no SIM. Plan for momentary dropouts.
