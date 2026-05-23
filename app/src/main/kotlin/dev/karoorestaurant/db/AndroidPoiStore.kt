package dev.karoorestaurant.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.Geo
import dev.karoorestaurant.data.route.LatLng
import java.time.Instant

class AndroidPoiStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DB_NAME, null, DB_VERSION,
), PoiStore {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE pois (
              osm_type TEXT NOT NULL,
              osm_id INTEGER NOT NULL,
              name TEXT NOT NULL,
              category TEXT NOT NULL,
              lat REAL NOT NULL,
              lon REAL NOT NULL,
              opening_hours_tag TEXT,
              fetched_at INTEGER NOT NULL,
              is_favorite INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY (osm_type, osm_id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_pois_bbox ON pois(lat, lon)")
        db.execSQL(
            "CREATE TABLE route_fetches (route_id TEXT PRIMARY KEY, fetched_at INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS route_fetches (route_id TEXT PRIMARY KEY, fetched_at INTEGER NOT NULL)"
            )
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE pois ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
        }
    }

    override fun upsertAll(pois: List<Poi>, fetchedAt: Instant) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (p in pois) {
                val cv = ContentValues().apply {
                    put("osm_type", p.osmType)
                    put("osm_id", p.osmId)
                    put("name", p.name)
                    put("category", p.category.name)
                    put("lat", p.lat)
                    put("lon", p.lon)
                    put("opening_hours_tag", p.openingHoursTag)
                    put("fetched_at", fetchedAt.toEpochMilli())
                    put("is_favorite", 0)
                }
                val inserted = db.insertWithOnConflict("pois", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                if (inserted == -1L) {
                    cv.remove("osm_type")
                    cv.remove("osm_id")
                    cv.remove("is_favorite")
                    db.update(
                        "pois",
                        cv,
                        "osm_type = ? AND osm_id = ?",
                        arrayOf(p.osmType, p.osmId.toString()),
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun setFavorite(poi: Poi, isFavorite: Boolean) {
        val cv = ContentValues().apply {
            put("is_favorite", if (isFavorite) 1 else 0)
        }
        writableDatabase.update(
            "pois",
            cv,
            "osm_type = ? AND osm_id = ?",
            arrayOf(poi.osmType, poi.osmId.toString()),
        )
    }

    override fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM pois", null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    override fun recordRouteFetch(routeId: String, fetchedAt: Instant) {
        val cv = ContentValues().apply {
            put("route_id", routeId)
            put("fetched_at", fetchedAt.toEpochMilli())
        }
        writableDatabase.insertWithOnConflict(
            "route_fetches", null, cv, SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun wasRouteFetched(routeId: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM route_fetches WHERE route_id = ? LIMIT 1",
            arrayOf(routeId),
        ).use { it.moveToFirst() }

    override fun nearest(
        center: LatLng,
        category: PoiCategory,
        maxMeters: Double,
        limit: Int,
        now: Instant,
        maxAgeDays: Long,
    ): List<NearbyHit> {
        val degLat = maxMeters / 111_320.0
        val degLon = maxMeters / (111_320.0 * Math.cos(Math.toRadians(center.lat)))
        val ageCutoffMillis = now.minusSeconds(maxAgeDays * 86_400L).toEpochMilli()
        val cursor = readableDatabase.rawQuery(
            """
            SELECT osm_type, osm_id, name, category, lat, lon, opening_hours_tag, fetched_at, is_favorite
            FROM pois
            WHERE category = ?
              AND lat BETWEEN ? AND ?
              AND lon BETWEEN ? AND ?
              AND (fetched_at > ? OR is_favorite = 1)
            """.trimIndent(),
            arrayOf(
                category.name,
                (center.lat - degLat).toString(),
                (center.lat + degLat).toString(),
                (center.lon - degLon).toString(),
                (center.lon + degLon).toString(),
                ageCutoffMillis.toString(),
            ),
        )
        val out = mutableListOf<NearbyHit>()
        cursor.use {
            while (it.moveToNext()) {
                val poi = Poi(
                    osmType = it.getString(0),
                    osmId = it.getLong(1),
                    name = it.getString(2),
                    category = PoiCategory.valueOf(it.getString(3)),
                    lat = it.getDouble(4),
                    lon = it.getDouble(5),
                    openingHoursTag = it.getString(6),
                )
                val fetchedAt = Instant.ofEpochMilli(it.getLong(7))
                val isFavorite = it.getInt(8) != 0
                val d = Geo.haversineMeters(center, LatLng(poi.lat, poi.lon))
                if (d <= maxMeters) out += NearbyHit(poi, d, fetchedAt, isFavorite)
            }
        }
        return out
            .sortedWith(compareByDescending<NearbyHit> { it.isFavorite }.thenBy { it.distanceMeters })
            .take(limit)
    }

    private companion object {
        const val DB_NAME = "pois.sqlite"
        const val DB_VERSION = 3
    }
}
