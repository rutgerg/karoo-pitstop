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
) {
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
              PRIMARY KEY (osm_type, osm_id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_pois_bbox ON pois(lat, lon)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS pois")
        onCreate(db)
    }

    fun upsertAll(pois: List<Poi>, fetchedAt: Instant = Instant.now()) {
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
                }
                db.insertWithOnConflict("pois", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM pois", null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    fun nearest(
        center: LatLng,
        category: PoiCategory,
        maxMeters: Double = 30_000.0,
        limit: Int = 25,
    ): List<Pair<Poi, Double>> {
        val degLat = maxMeters / 111_320.0
        val degLon = maxMeters / (111_320.0 * Math.cos(Math.toRadians(center.lat)))
        val cursor = readableDatabase.rawQuery(
            """
            SELECT osm_type, osm_id, name, category, lat, lon, opening_hours_tag
            FROM pois
            WHERE category = ?
              AND lat BETWEEN ? AND ?
              AND lon BETWEEN ? AND ?
            """.trimIndent(),
            arrayOf(
                category.name,
                (center.lat - degLat).toString(),
                (center.lat + degLat).toString(),
                (center.lon - degLon).toString(),
                (center.lon + degLon).toString(),
            ),
        )
        val out = mutableListOf<Pair<Poi, Double>>()
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
                val d = Geo.haversineMeters(center, LatLng(poi.lat, poi.lon))
                if (d <= maxMeters) out += poi to d
            }
        }
        return out.sortedBy { it.second }.take(limit)
    }

    private companion object {
        const val DB_NAME = "pois.sqlite"
        const val DB_VERSION = 1
    }
}
