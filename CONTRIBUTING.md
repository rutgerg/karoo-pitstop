# Contributing to Pitstop

Thanks for your interest. Pitstop is a small project — bug reports, feature ideas, and code contributions are all welcome.

## Reporting a bug

[Open an issue](https://github.com/rutgerg/karoo-pitstop/issues/new). What helps:

- Karoo OS version (Settings → System → About)
- What was on screen — was a route loaded, did the GPS have a fix, which tiles were active?
- Wi-Fi state if a fetch failed
- A relevant `adb logcat | grep HHApp` snippet if you can run dev-tools

## Proposing a feature

[Open an issue](https://github.com/rutgerg/karoo-pitstop/issues/new) describing the **use case** rather than the implementation. Helpful framing:

- Which existing category does it relate to (Restaurant, Supermarket, Fuel, Cafe, Hotel, Doctor, Pharmacy, Bike Shop, ATM)?
- Or is it a new category? OSM's `amenity` / `shop` / `tourism` tags are the source of truth — see [`PoiCategory.kt`](data/src/main/kotlin/dev/karoorestaurant/data/poi/PoiCategory.kt).
- What ride context — audax, bikepacking, commute, gravel?

## Submitting a code change

1. Fork or branch from `main` with a short kebab-case branch name (e.g. `add-bar-category`).
2. Build and run unit tests:

   ```bash
   ./gradlew :data:test :app:testDebugUnitTest
   ```
3. For changes that affect the running app (not docs/config), sideload a debug build to a Karoo and confirm:
   - The 9 tiles still render with live data
   - Tapping a tile opens the Karoo pin activity
4. Open a PR with a clear title, what + why in the body, and `Closes #N` or `Related to #N` linking to an issue.

**Especially welcomed:** Karoo 2 testing. Pitstop is only verified on Karoo 3 today — if you have a Karoo 2, please try it and report back via an issue.

## Building from source

See [Build from source](README.md#build-from-source) in the README.

## License

Pitstop is [MIT-licensed](LICENSE). Contributions are accepted under the same license. No CLA, no DCO.
