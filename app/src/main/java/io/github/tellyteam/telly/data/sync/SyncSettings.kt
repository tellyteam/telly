package io.github.tellyteam.telly.data.sync

import android.content.Context
import io.github.tellyteam.telly.data.store.KeystoreCipher
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The link code (the household's only secret), this device's id and the relay. Encrypted on the device. */
@Serializable
data class SyncConfig(val code: String, val device: String, val relay: String)

class SyncSettings(context: Context) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher("telly_sync")

    fun load(): SyncConfig? = prefs.getString(KEY, null)?.let { stored ->
        runCatching { json.decodeFromString(SyncConfig.serializer(), cipher.decrypt(stored)) }.getOrNull()
    }?.takeIf { SyncCrypto.normaliseCode(it.code) != null && it.relay.startsWith("https://") }

    fun save(config: SyncConfig) {
        prefs.edit().putString(KEY, cipher.encrypt(json.encodeToString(SyncConfig.serializer(), config))).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        /** The relay telly uses unless you run your own (see telly-sync). */
        const val DEFAULT_RELAY = ""
        private const val KEY = "config"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
