package dev.karoorestaurant.data.store

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.Geo
import dev.karoorestaurant.data.route.LatLng
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

class PoiStore(private val connection: Connection) : AutoCloseable {

    init {
        connection.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS pois (
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
            st.execute("CREATE INDEX IF NOT EXISTS idx_pois_bbox ON pois(lat, lon)")
        }
    }

    fun upsertAll(pois: List<Poi>, fetchedAt: Instant = Instant.now()) {
        val sql = """
            INSERT INTO pois(osm_type, osm_id, name, category, lat, lon, opening_hours_tag, fetched_at)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(osm_type, osm_id) DO UPDATE SET
              name=excluded.name,
              category=excluded.category,
              lat=excluded.lat,
              lon=excluded.lon,
              opening_hours_tag=excluded.opening_hours_tag,
              fetched_at=excluded.fetched_at
        """.trimIndent()
        connection.autoCommit = false
        try {
            connection.prepareStatement(sql).use { ps ->
                for (poi in pois) {
                    ps.setString(1, poi.osmType)
                    ps.setLong(2, poi.osmId)
                    ps.setString(3, poi.name)
                    ps.setString(4, poi.category.name)
                    ps.setDouble(5, poi.lat)
                    ps.setDouble(6, poi.lon)
                    if (poi.openingHoursTag != null) ps.setString(7, poi.openingHoursTag)
                    else ps.setNull(7, java.sql.Types.VARCHAR)
                    ps.setLong(8, fetchedAt.toEpochMilli())
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            connection.commit()
        } catch (t: Throwable) {
            connection.rollback()
            throw t
        } finally {
            connection.autoCommit = true
        }
    }

    fun count(): Int =
        connection.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM pois").use { rs ->
                rs.next(); rs.getInt(1)
            }
        }

    fun countWithOpeningHours(): Int =
        connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT COUNT(*) FROM pois WHERE opening_hours_tag IS NOT NULL AND opening_hours_tag != ''"
            ).use { rs -> rs.next(); rs.getInt(1) }
        }

    fun countByCategory(): Map<PoiCategory, Int> {
        val out = mutableMapOf<PoiCategory, Int>()
        connection.createStatement().use { st ->
            st.executeQuery("SELECT category, COUNT(*) FROM pois GROUP BY category").use { rs ->
                while (rs.next()) {
                    val c = runCatching { PoiCategory.valueOf(rs.getString(1)) }.getOrNull()
                    if (c != null) out[c] = rs.getInt(2)
                }
            }
        }
        return out
    }

    fun nearest(
        center: LatLng,
        category: PoiCategory,
        maxMeters: Double = 30_000.0,
        limit: Int = 25,
    ): List<Pair<Poi, Double>> {
        val degLat = maxMeters / 111_320.0
        val degLon = maxMeters / (111_320.0 * Math.cos(Math.toRadians(center.lat)))
        val sql = """
            SELECT osm_type, osm_id, name, category, lat, lon, opening_hours_tag
            FROM pois
            WHERE category = ?
              AND lat BETWEEN ? AND ?
              AND lon BETWEEN ? AND ?
        """.trimIndent()
        val results = mutableListOf<Pair<Poi, Double>>()
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, category.name)
            ps.setDouble(2, center.lat - degLat)
            ps.setDouble(3, center.lat + degLat)
            ps.setDouble(4, center.lon - degLon)
            ps.setDouble(5, center.lon + degLon)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val poi = Poi(
                        osmType = rs.getString(1),
                        osmId = rs.getLong(2),
                        name = rs.getString(3),
                        category = PoiCategory.valueOf(rs.getString(4)),
                        lat = rs.getDouble(5),
                        lon = rs.getDouble(6),
                        openingHoursTag = rs.getString(7),
                    )
                    val d = Geo.haversineMeters(center, LatLng(poi.lat, poi.lon))
                    if (d <= maxMeters) results += poi to d
                }
            }
        }
        return results.sortedBy { it.second }.take(limit)
    }

    override fun close() {
        connection.close()
    }

    companion object {
        fun open(path: String): PoiStore {
            Class.forName("org.sqlite.JDBC")
            val conn = DriverManager.getConnection("jdbc:sqlite:$path")
            return PoiStore(conn)
        }
    }
}
