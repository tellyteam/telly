package io.github.tellyteam.telly.ui.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import io.github.tellyteam.telly.container
import io.github.tellyteam.telly.data.Channel
import io.github.tellyteam.telly.data.Provider
import io.github.tellyteam.telly.data.guide.NowNext

/**
 * Now/next for a channel: the cached guide at once, otherwise fetched after [settleMs] (so rows
 * scrolled straight past never trigger a request). Re-checks every minute so it rolls over when
 * a programme ends.
 */
@Composable
fun rememberNowNext(channel: Channel?, provider: Provider?, settleMs: Long = 0): NowNext? {
    val guide = LocalContext.current.container.guide
    val nowNext by produceState(channel?.let { guide.cached(it) }, channel?.id, provider) {
        if (channel == null || provider == null) return@produceState
        if (value == null && settleMs > 0) delay(settleMs)
        while (true) {
            value = guide.nowNext(provider, channel) ?: value
            delay(60_000)
        }
    }
    return nowNext
}

/** "18:00" in the TV's local time. */
fun clock(ms: Long): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date(ms))
