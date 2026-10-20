package io.github.tellyteam.telly.data.sync

import java.net.URLEncoder

/**
 * The address a QR code carries to link another device: `telly://sync?code=…`, plus `&relay=…`
 * only for a relay other than telly's own. An iPad's camera opens telly with it (PROTOCOL.md).
 */
object SyncLink {
    fun url(code: String, relay: String): String {
        val base = "telly://sync?code=$code"
        return if (relay.trimEnd('/') == SyncSettings.DEFAULT_RELAY) base else "$base&relay=${URLEncoder.encode(relay, "UTF-8")}"
    }
}
