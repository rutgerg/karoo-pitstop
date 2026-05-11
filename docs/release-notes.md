# Release notes

User-facing notes per `vX.Y.Z` release. The release workflow extracts the section matching the current tag and pipes it into the Karoo App Manifest as `releaseNotes`, where it appears in the Karoo extension About panel. Add a new `## X.Y.Z` section at the top before tagging.

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
