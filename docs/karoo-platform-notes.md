# Karoo platform notes

Things discovered building Pitstop that are not obvious from the `karoo-ext` 1.1.8 docs. Recorded here so future-you (or anyone forking this) does not re-derive them on a frustrating afternoon.

## Entry points and surfaces

- **There is no third-party app drawer on the Karoo 3.** The home screen is the Hammerhead launcher and does not enumerate activities with `LAUNCHER` intent filters. A traditional Android-style "tap an icon to launch the app" path does not exist for sideloaded apps.
- **`KarooExtension` is an Android `Service`, not an Activity wrapper.** Registering one does not give your app a tappable icon anywhere. The only on-device surfaces it exposes are: data field tiles (`DataTypeImpl`), map overlays (`MapEffect`), bonus actions on paired controllers (`BonusAction`), and FIT-file effects.
- **`BonusAction` requires a paired hardware controller.** It surfaces only as an assignable function on a SRAM AXS-style remote (Blip / Eagle / similar). Without one paired, `BonusAction` has no UI to bind to.
- **Third-party Activities do not get a Karoo-provided back button.** The pill-shaped back affordance visible in the Hammerhead Extensions list and other system screens is rendered by the launcher, not overlaid on third-party Activities at runtime. Verified 2026-05-07 by shipping Pitstop's Settings screen with no back button (#118 / PR #119) and observing no fallback. Apps reachable via `Extensions → <app> → Open` must draw their own back affordance and call `finish()` on tap.

## Service lifecycle

- **The Karoo App Store starts extensions as foreground services on `BOOT_COMPLETED`.** Your `KarooExtension` subclass *must* call `startForeground(notificationId, notification)` in `onCreate()` within ~5 seconds. If it doesn't, Android ANRs the service and the App Store removes it from the registered extensions list. Symptom: your data types never appear in the Karoo Pages data-type picker and `adb logcat` shows `Context.startForegroundService() did not then call Service.startForeground()` for your package.
- **`android:foregroundServiceType` is required on `targetSdk` 34+.** Use `dataSync` plus `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />` to keep the manifest valid.
- **The Karoo system logs extension events under tag `HHApp: Extensions:`**, not under the karoo-ext SDK's `KarooExtension` tag. Filter logcat with `grep -i HHApp` if you are looking for bind/connect/disconnect events.

## Connectivity

- **The Karoo 3 has Wi-Fi but no SIM, and Pitstop does not use the karoo-ext HTTP bridge.** The watch reaches the internet directly over OkHttp on whatever Wi-Fi network it is joined to — home Wi-Fi at the charging dock, or a phone hotspot during a ride. When no Wi-Fi is available, fetches fail silently and tiles render from the local SQLite cache.
- **The karoo-ext `OnHttpResponse` bridge exists but is unsuitable for bulk POI fetches.** It relays HTTP through a paired phone's BT companion app and enforces a 100 KB request/response cap (`OnHttpResponse.MAX_REQUEST_SIZE` in `karoo-ext-1.1.8`). A typical city-density single-category Overpass response exceeds that cap, so Pitstop bypasses the bridge entirely. The bridge is designed for in-ride micro-API calls (Strava live segments, weather widgets), not bulk prefetch.
- **The README's earlier claim that direct OkHttp cannot reach the internet on a Karoo without a SIM is wrong** when the device is on Wi-Fi. The kernel `main` routing table is empty (which is misleading from a `ip route` shell), but Android uses per-network route tables addressed by the active `Network`. Any app holding `INTERNET` permission routes via the active Wi-Fi network just like any other Android app.

## Routing

- **There is no public "add waypoint" or "insert stop" effect** in `karoo-ext` 1.1.8. The closest available effect is `LaunchPinDrop`, which opens the Karoo's pin activity giving the user **Navigate to** (replaces the active route) or **Save as POI** (bookmarks). Inserting a stop into an existing route without destroying it is not exposed to third-party extensions.

## SDK distribution

- **`karoo-ext` 1.1.8 is published only to GitHub Packages**, with mandatory PAT (`read:packages` scope) auth even for public reads. No JitPack or Maven Central mirror.

## Tile rendering

- **Data tiles use `RemoteViews`**, so layouts are limited to the subset Android exposes for cross-process inflation (`FrameLayout`, `LinearLayout`, `TextView`, `ImageView`, etc.). The Karoo Pages app does honor `setOnClickPendingIntent` on the root view (verified on hardware), so tiles can be made tappable.
- **Hardcoded white text** on tiles is fine in practice — Karoo Pages renders data fields on dark backgrounds. There is no public theme attribute exposed to extensions to follow user theme choice.

## Debug-broadcast quirks

- **`am broadcast` on Karoo Android does not accept `--ed` (double extras).** Use `--es` (string) and parse to double in the receiver.
- **Manifest receivers do not get implicit broadcasts in the background** (Android 8+). Scope test broadcasts to your package with `-p <pkg>` to make them explicit; otherwise they are silently dropped.
- **`adb install -r` puts the app in stopped state.** Pass `--include-stopped-packages` to subsequent `am broadcast` calls or wake the service first via `am start-foreground-service -n <pkg>/.<Service>`.
