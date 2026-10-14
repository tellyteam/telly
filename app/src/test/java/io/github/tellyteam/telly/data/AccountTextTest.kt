package io.github.tellyteam.telly.data

import io.github.tellyteam.telly.data.xtream.XtreamParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class AccountTextTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = 1_791_000_000_000L // 2026-10-03

    @Test
    fun `reads the subscription from user_info`() {
        val a = XtreamParser.account(
            Json.parseToJsonElement("""{"user_info":{"auth":1,"status":"Active","exp_date":"1803945600","max_connections":"2","active_cons":"1","is_trial":"0"}}"""),
        )
        assertEquals(1_803_945_600_000L, a.expiresAtMs)
        assertEquals(2, a.maxConnections)
        assertEquals(1, a.activeConnections)
        assertEquals("Active · ends 2 March 2027 (in 149 days)", AccountText.summary(a, now, utc))
        assertEquals("1 of 2 connections in use", AccountText.connections(a))
    }

    @Test
    fun `no end date, trials and endings read naturally`() {
        val never = XtreamParser.account(Json.parseToJsonElement("""{"user_info":{"auth":1,"status":"Active","exp_date":null,"max_connections":1}}"""))
        assertNull(never.expiresAtMs)
        assertEquals("Active · no end date", AccountText.summary(never, now, utc))
        assertEquals("1 connection allowed", AccountText.connections(never))
        val trial = never.copy(trial = true, expiresAtMs = now + 36 * 60 * 60 * 1000L)
        assertTrue(AccountText.summary(trial, now, utc).startsWith("Trial · ends 4 October 2026 (tomorrow)"))
        val ended = never.copy(expiresAtMs = now - 3 * 24 * 60 * 60 * 1000L)
        assertEquals("Active · ended 30 September 2026", AccountText.summary(ended, now, utc))
    }

    @Test
    fun `reminder wording`() {
        assertEquals("Your subscription ends today.", AccountText.reminder(0))
        assertEquals("Your subscription ends tomorrow.", AccountText.reminder(1))
        assertEquals("Your subscription ends in 5 days.", AccountText.reminder(5))
        assertEquals(0L, AccountText.daysLeft(now + 1000, now))
        assertEquals(-1L, AccountText.daysLeft(now - 1000, now))
    }
}
