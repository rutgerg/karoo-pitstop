# Project shape

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
│       ├── MainActivity.kt                           Compose Settings screen; the only Activity, launched from Karoo Extensions → Pitstop → Open
│       ├── PoiNearby.kt                              UI display model
│       ├── settings/SettingsRepository.kt            DataStore-backed telemetryEnabled (StateFlow + suspend setter)
│       ├── telemetry/{InstallId, TelemetryCounters, HeartbeatSender, Telemetry}.kt
│       ├── db/{PoiStore,AndroidPoiStore}.kt          interface + SQLiteOpenHelper impl
│       └── ui/Theme.kt
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
