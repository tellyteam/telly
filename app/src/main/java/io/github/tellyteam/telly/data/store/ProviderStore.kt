package io.github.tellyteam.telly.data.store

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import io.github.tellyteam.telly.data.DEFAULT_PLAYLIST_NAME
import io.github.tellyteam.telly.data.DEFAULT_PROVIDER_NAME
import io.github.tellyteam.telly.data.Provider

/** Keeps the single configured provider, encrypted at rest. */
class ProviderStore(context: Context) {
    private val prefs = context.getSharedPreferences("provider", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher("telly_provider")

    /** Returns null when nothing is saved or the saved value can no longer be decrypted. */
    fun load(): Provider? = runCatching {
        prefs.getString(KEY, null)?.let { decode(cipher.decrypt(it)) }
    }.getOrNull()

    fun save(provider: Provider) {
        prefs.edit().putString(KEY, cipher.encrypt(encode(provider))).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    internal companion object {
        private const val KEY = "provider"

        fun encode(provider: Provider): String = buildJsonObject {
            put("name", provider.name)
            put("allowCleartext", provider.allowCleartext)
            when (provider) {
                is Provider.Xtream -> {
                    put("type", "xtream")
                    put("server", provider.server)
                    put("username", provider.username)
                    put("password", provider.password)
                }
                is Provider.M3u -> {
                    put("type", "m3u")
                    put("url", provider.url)
                }
            }
        }.toString()

        fun decode(text: String): Provider {
            val o = Json.parseToJsonElement(text).jsonObject
            // Earlier versions' default names.
            val name = when (val saved = o.str("name")) {
                "My provider" -> DEFAULT_PROVIDER_NAME
                "My playlist" -> DEFAULT_PLAYLIST_NAME
                else -> saved
            }
            val allowCleartext = (o["allowCleartext"] as? JsonPrimitive)?.booleanOrNull ?: false
            return when (o.str("type")) {
                "xtream" -> Provider.Xtream(name, o.str("server"), o.str("username"), o.str("password"), allowCleartext)
                "m3u" -> Provider.M3u(name, o.str("url"), allowCleartext)
                else -> error("Unknown provider type")
            }
        }

        private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
}
