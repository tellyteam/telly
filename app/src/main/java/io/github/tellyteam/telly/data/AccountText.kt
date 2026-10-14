package io.github.tellyteam.telly.data

import io.github.tellyteam.telly.data.xtream.XtreamParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The subscription's details in words, for Settings and the expiry reminder. Unit-tested. */
object AccountText {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Whole days left; 0 on the last day, negative once it has ended. */
    fun daysLeft(expiresAtMs: Long, nowMs: Long): Long = Math.floorDiv(expiresAtMs - nowMs, DAY_MS)

    fun reminder(days: Long): String = when (days) {
        0L -> "Your subscription ends today."
        1L -> "Your subscription ends tomorrow."
        else -> "Your subscription ends in $days days."
    }

    /** "Active · ends 12 March 2027 (in 149 days)", "Trial · ends …", "Active · no end date". */
    fun summary(a: XtreamParser.Account, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val status = when {
            a.trial -> "Trial"
            a.status.isNullOrBlank() -> "Active"
            else -> a.status.replaceFirstChar { it.uppercase() }
        }
        val end = a.expiresAtMs ?: return "$status · no end date"
        val date = SimpleDateFormat("d MMMM yyyy", Locale.UK).apply { timeZone = zone }.format(Date(end))
        val days = daysLeft(end, nowMs)
        val left = when {
            days < 0 -> "ended"
            days == 0L -> "today"
            days == 1L -> "tomorrow"
            else -> "in $days days"
        }
        return if (days < 0) "$status · ended $date" else "$status · ends $date ($left)"
    }

    /** "1 of 2 connections in use" (this TV isn't streaming while Settings is open). */
    fun connections(a: XtreamParser.Account): String? {
        val max = a.maxConnections ?: return null
        val active = a.activeConnections ?: return "$max connection${if (max == 1) "" else "s"} allowed"
        return "$active of $max connection${if (max == 1) "" else "s"} in use"
    }
}
