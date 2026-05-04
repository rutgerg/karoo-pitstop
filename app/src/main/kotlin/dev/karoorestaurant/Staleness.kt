package dev.karoorestaurant

import java.time.Duration
import java.time.Instant

/**
 * Cache age band for a stored POI.
 *
 * - [NEW] (<14 days) — render at face value.
 * - [AGING] (14–60 days) — render but with an "unverified" sub-badge so the rider knows
 *   to treat the data with skepticism.
 * - [EXPIRED] (>60 days) — drop entirely; the source data is too likely to be wrong.
 */
enum class Staleness { NEW, AGING, EXPIRED }

const val STALENESS_AGING_DAYS: Long = 14L
const val STALENESS_EXPIRED_DAYS: Long = 60L

fun stalenessOf(fetchedAt: Instant, now: Instant = Instant.now()): Staleness {
    val ageDays = Duration.between(fetchedAt, now).toDays()
    return when {
        ageDays < STALENESS_AGING_DAYS -> Staleness.NEW
        ageDays < STALENESS_EXPIRED_DAYS -> Staleness.AGING
        else -> Staleness.EXPIRED
    }
}
