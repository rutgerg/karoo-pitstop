# Troubleshooting

Common issues building, installing, or running Pitstop. For platform-level karoo-ext gotchas, see [karoo-platform-notes.md](karoo-platform-notes.md).

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
