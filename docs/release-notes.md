# Release notes

User-facing notes per `vX.Y.Z` release. The release workflow extracts the section matching the current tag and pipes it into the Karoo App Manifest as `releaseNotes`, where it appears in the Karoo extension About panel. Add a new `## X.Y.Z` section at the top before tagging.

## 1.6.1

- POI names on each tile are now color-coded by open status: green for open, orange for unknown opening hours, red for closed. The status text line is gone, freeing room for longer POI names.
- New Show closed POIs setting, off by default. When off, each tile skips closed POIs and picks the next nearest open or unknown one. Turn it on to keep the previous behavior.
- The Water Refill tile now also includes campsites. Many campsites have outdoor taps, showers, or toilets, making them a useful refill option in rural areas where dedicated drinking-water nodes are sparse.

## 1.6.0

- POI names on each tile are now color-coded by open status: green for open, orange for unknown opening hours, red for closed. The status text line is gone, freeing room for longer POI names.
- New Show closed POIs setting, off by default. When off, each tile skips closed POIs and picks the next nearest open or unknown one. Turn it on to keep the previous behavior.
- The Water Refill tile now also includes campsites. Many campsites have outdoor taps, showers, or toilets, making them a useful refill option in rural areas where dedicated drinking-water nodes are sparse.

## 1.5.2

- Newly added POI categories now back-fill on the next ride for routes you cached in an older version of the app. The Water Refill tile shipped in 1.5.0 now populates for routes you had previously ridden before installing 1.5.0, instead of waiting until you load a brand-new route in an area that has not been fetched before.
- New Water Refill tile — surfaces the nearest drinking-water tap, public toilet, or cemetery along the route. Cemeteries are included because they very often have an outdoor tap, which makes them a useful fallback in rural areas where a dedicated drinking_water node is missing.

## 1.5.1

- The Karoo Extensions About panel description now mentions the Water Refill tile shipped in 1.5.0. No functional changes.

## 1.5.0

- New Water Refill tile — surfaces the nearest drinking-water tap, public toilet, or cemetery along the route. Cemeteries are included because they very often have an outdoor tap, which makes them a useful fallback in rural areas where a dedicated drinking_water node is missing.

## 1.4.2

- New **Train Station** tile — add it to a data page to see the nearest station along the route. Small unstaffed halts (common in NL/BE/DE) are included.
- The **Restaurant** tile now also surfaces fast food, kebab and shawarma shops, frituren, and snack bars — the food you actually pass on a touring ride.
- Cached POIs keep showing on the tile even when a background refresh fails, so a brief Wi-Fi blip no longer wipes a working tile.
- Faster recovery from spotty Wi-Fi: more retry attempts and a tighter cadence, so the cache fills sooner once a connection is back.
- POI lookups now use Cloudflare DNS-over-HTTPS to bypass the Karoo's system resolver, which has been observed failing on some home Wi-Fi networks even when the network is otherwise working.

## 1.4.1

- The Karoo Extensions About panel description now mentions the Train Station tile shipped in 1.4.0. No functional changes.

## 1.4.0

- New **Train Station** tile — add it to a data page to see the nearest station along the route. Small unstaffed halts (common in NL/BE/DE) are included.
- The **Restaurant** tile now also surfaces fast food, kebab and shawarma shops, frituren, and snack bars — the food you actually pass on a touring ride.
- Cached POIs keep showing on the tile even when a background refresh fails, so a brief Wi-Fi blip no longer wipes a working tile.
- Faster recovery from spotty Wi-Fi: more retry attempts and a tighter cadence, so the cache fills sooner once a connection is back.
- POI lookups now use Cloudflare DNS-over-HTTPS to bypass the Karoo's system resolver, which has been observed failing on some home Wi-Fi networks even when the network is otherwise working.

## 1.3.1

- POIs now refresh every 20 minutes around your current location during a ride, so the cache follows you instead of staying tied to where the route started.
- Tile shows Waiting for Wi-Fi when the device is offline and automatically retries as soon as Wi-Fi returns, instead of giving up after a few attempts.
- Larger left-aligned blue direction arrow on each tile, with tighter padding so more of the POI name fits.

## 1.3.0

- New Pitstop logo: a coffee mug with a drop-bar bicycle handlebar. Now visible next to "Pitstop" in the Karoo Extensions list.

## 1.2.0

- Cleaner tile design: simplified Open / Closed / Unknown status line, blue direction arrow, tighter padding.
- Tap a tile to open the Karoo pin activity — choose Navigate or Save as POI.
- Upgrade note: existing installs must be uninstalled once before sideloading this build (one-time signing-key change).
