package dev.karoorestaurant.data.poi

import ch.poole.openinghoursparser.OpeningHoursParser
import ch.poole.openinghoursparser.Rule
import ch.poole.openinghoursparser.RuleModifier
import ch.poole.openinghoursparser.WeekDay
import java.io.ByteArrayInputStream
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Minimal "is open at instant T" evaluator over the AST produced by ch.poole:OpeningHoursParser.
 *
 * Handles the common 80%: 24/7, weekday + time-span lists, multi-rule overrides with `off`.
 * Does NOT handle: public holidays (PH), date ranges (Apr-Oct), week numbers, sunset/sunrise,
 * or comments. Unknown rule shapes resolve to `Unknown` so the caller can choose to surface
 * them with an "unverified" badge instead of dropping silently.
 */
object OpeningHours {

    sealed class Status {
        data object Open : Status()
        data object Closed : Status()
        data class Unknown(val reason: String) : Status()
    }

    fun evaluate(tag: String?, now: LocalDateTime = LocalDateTime.now()): Status {
        if (tag.isNullOrBlank()) return Status.Unknown("no opening_hours tag")
        val trimmed = tag.trim()
        if (trimmed == "24/7") return Status.Open
        if (trimmed.equals("closed", ignoreCase = true) ||
            trimmed.equals("off", ignoreCase = true)) return Status.Closed

        val rules = try {
            OpeningHoursParser(ByteArrayInputStream(trimmed.toByteArray()))
                .rules(false)
        } catch (e: Exception) {
            return Status.Unknown("parse error: ${e.message}")
        }

        var verdict: Status = Status.Closed
        var sawApplicable = false
        for (rule in rules) {
            when (val applies = ruleAppliesNow(rule, now)) {
                Applicability.UNSUPPORTED -> return Status.Unknown("complex rule")
                Applicability.NO -> continue
                Applicability.YES -> {
                    sawApplicable = true
                    val mod = rule.modifier?.modifier
                    verdict = if (mod == RuleModifier.Modifier.CLOSED ||
                        mod == RuleModifier.Modifier.OFF) Status.Closed else Status.Open
                }
            }
        }
        return if (sawApplicable) verdict else Status.Closed
    }

    private enum class Applicability { YES, NO, UNSUPPORTED }

    private fun ruleAppliesNow(rule: Rule, now: LocalDateTime): Applicability {
        if (!rule.dates.isNullOrEmpty()) return Applicability.UNSUPPORTED
        if (!rule.years.isNullOrEmpty()) return Applicability.UNSUPPORTED
        if (!rule.weeks.isNullOrEmpty()) return Applicability.UNSUPPORTED
        if (!rule.holidays.isNullOrEmpty()) return Applicability.UNSUPPORTED

        val today = now.dayOfWeek
        val days = rule.days
        val dayMatches: Boolean = if (days.isNullOrEmpty()) {
            true
        } else {
            var any = false
            for (range in days) {
                val start = range.startDay?.toJavaDayOfWeek() ?: return Applicability.UNSUPPORTED
                val end = (range.endDay ?: range.startDay)?.toJavaDayOfWeek()
                    ?: return Applicability.UNSUPPORTED
                if (isDayInRange(today, start, end)) {
                    any = true
                    break
                }
            }
            any
        }
        if (!dayMatches) return Applicability.NO

        val times = rule.times
        if (times.isNullOrEmpty()) return Applicability.YES
        val nowTime = now.toLocalTime()
        for (span in times) {
            val startMin = span.start
            val endMin = span.end
            if (startMin < 0 || endMin < 0) return Applicability.UNSUPPORTED
            val start = minutesToTime(startMin)
            val end = minutesToTime(endMin)
            val match = when {
                end == LocalTime.MIDNIGHT || endMin >= 24 * 60 -> !nowTime.isBefore(start)
                end.isBefore(start) -> !nowTime.isBefore(start) || nowTime.isBefore(end)
                else -> !nowTime.isBefore(start) && nowTime.isBefore(end)
            }
            if (match) return Applicability.YES
        }
        return Applicability.NO
    }

    private fun minutesToTime(minutes: Int): LocalTime =
        if (minutes >= 24 * 60) LocalTime.MIDNIGHT
        else LocalTime.of(minutes / 60, minutes % 60)

    private fun isDayInRange(day: DayOfWeek, start: DayOfWeek, end: DayOfWeek): Boolean {
        val s = start.value
        val e = end.value
        val d = day.value
        return if (s <= e) d in s..e else d >= s || d <= e
    }

    private fun WeekDay.toJavaDayOfWeek(): DayOfWeek? = when (this) {
        WeekDay.MO -> DayOfWeek.MONDAY
        WeekDay.TU -> DayOfWeek.TUESDAY
        WeekDay.WE -> DayOfWeek.WEDNESDAY
        WeekDay.TH -> DayOfWeek.THURSDAY
        WeekDay.FR -> DayOfWeek.FRIDAY
        WeekDay.SA -> DayOfWeek.SATURDAY
        WeekDay.SU -> DayOfWeek.SUNDAY
        else -> null
    }
}
