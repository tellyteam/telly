package io.github.tellyteam.telly.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import io.github.tellyteam.telly.data.DEFAULT_PLAYLIST_NAME
import io.github.tellyteam.telly.data.DEFAULT_PROVIDER_NAME
import io.github.tellyteam.telly.data.Provider
import io.github.tellyteam.telly.data.syncIdentity

/**
 * A whole parcel (version 2): a section for each provider the device has, under its provider tag,
 * and the opt-in logins. Reads version 1 parcels too, as one section for the provider the reader
 * says (its only one: with several it can't tell whose a version 1 parcel is).
 */
data class SyncBundle(
    val at: Long = 0,
    val sections: Map<String, SyncParcel> = emptyMap(),
    /** The opt-in logins, by provider tag; null when this device doesn't share them (or the parcel has none). */
    val logins: Map<String, SharedLogin>? = null,
) {
    /** The parcel; with [keys] and [device], any logins go in sealed. */
    fun toJson(keys: SyncCrypto.Keys? = null, device: String? = null): String = buildJsonObject {
        put("v", 2)
        put("at", at)
        put("providers", JsonObject(
            sections.entries.sortedByDescending { it.value.at }.take(MAX_SECTIONS)
                .associate { (tag, section) -> tag to SyncParcel.trim(section, at).sectionObject() },
        ))
        if (logins != null && keys != null && device != null) {
            put("logins", Base64Text.encode(SyncCrypto.sealLogins(keys, device, SharedLogin.text(SharedLogin.trim(logins, at)))))
        }
    }.toString()

    companion object {
        const val MAX_SECTIONS = 20
        private val TAG = Regex("^[0-9a-f]{16}$")

        fun isTag(tag: String) = TAG.matches(tag)

        /**
         * Another device's parcel. [current] is the tag a version 1 parcel is read as (null: it's
         * ignored). With [keys] and the sending [device], its logins are opened too, each checked
         * against its own tag.
         */
        fun parse(text: String, current: String?, keys: SyncCrypto.Keys? = null, device: String? = null): SyncBundle? {
            val raw = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
            val at = SyncParcel.time(raw["at"]) ?: 0
            return when (SyncParcel.time(raw["v"])) {
                1L -> SyncBundle(at, if (current == null) emptyMap() else mapOf(current to SyncParcel.parseSection(raw, at)))
                2L -> {
                    val sections = LinkedHashMap<String, SyncParcel>()
                    (raw["providers"] as? JsonObject)?.forEach { (tag, value) ->
                        val section = value as? JsonObject
                        if (isTag(tag) && section != null && sections.size < MAX_SECTIONS) sections[tag] = SyncParcel.parseSection(section, at)
                    }
                    val logins = if (keys != null && device != null) {
                        (raw["logins"] as? JsonPrimitive)?.contentOrNull?.let(Base64Text::decode)
                            ?.let { SyncCrypto.openLogins(keys, device, it) }?.let { SharedLogin.parse(it, keys) }
                    } else null
                    SyncBundle(at, sections, logins)
                }
                else -> null
            }
        }

        /** [own] combined with others, section by section, for the providers [own] has; and the logins, newest per tag, if [own] shares them. */
        fun merge(own: SyncBundle, others: List<SyncBundle>, now: Long): SyncBundle {
            val sections = own.sections.mapValues { (tag, section) -> SyncParcel.merge(section, others.mapNotNull { it.sections[tag] }, now) }
            val logins = own.logins?.let { mine ->
                val all = mine.toMutableMap()
                others.mapNotNull { it.logins }.forEach { theirs ->
                    theirs.forEach { (tag, login) -> if ((all[tag]?.at ?: -1) < login.at) all[tag] = login }
                }
                SharedLogin.trim(all, now)
            }
            return SyncBundle(now, sections, logins)
        }
    }
}

/**
 * A provider's login as shared between devices that opt in (PROTOCOL.md, "Logins"), or its removal
 * (no [provider]). Written in the same JSON shape by every telly app.
 */
data class SharedLogin(val at: Long, val provider: Provider?) {
    fun toJson(): JsonObject = buildJsonObject {
        put("at", at)
        when (provider) {
            null -> put("removed", true)
            is Provider.Xtream -> {
                put("kind", "xtream")
                put("name", provider.name)
                put("server", provider.server)
                put("username", provider.username)
                put("password", provider.password)
                put("allowCleartext", provider.allowCleartext)
            }
            is Provider.M3u -> {
                put("kind", "m3u")
                put("name", provider.name)
                put("url", provider.url)
                put("allowCleartext", provider.allowCleartext)
            }
        }
    }

    companion object {
        const val MAX = 20

        /** Drops removals past the tombstone time, and keeps the newest [MAX]. */
        fun trim(logins: Map<String, SharedLogin>, now: Long): Map<String, SharedLogin> =
            logins.entries.filter { it.value.provider != null || now - it.value.at < SyncParcel.TOMBSTONE_MS }
                .sortedByDescending { it.value.at }.take(MAX).associate { it.key to it.value }

        fun text(logins: Map<String, SharedLogin>): String = JsonObject(logins.mapValues { it.value.toJson() }).toString()

        /** Logins another device shared, keeping only those whose tag matches their own identity. */
        fun parse(text: String, keys: SyncCrypto.Keys): Map<String, SharedLogin> {
            val raw = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyMap()
            val out = LinkedHashMap<String, SharedLogin>()
            raw.forEach { (tag, value) ->
                val o = value as? JsonObject ?: return@forEach
                val at = SyncParcel.time(o["at"]) ?: return@forEach
                if (!SyncBundle.isTag(tag)) return@forEach
                if ((o["removed"] as? JsonPrimitive)?.booleanOrNull == true) {
                    out[tag] = SharedLogin(at, null)
                    return@forEach
                }
                fun str(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
                val allow = (o["allowCleartext"] as? JsonPrimitive)?.booleanOrNull ?: false
                val p: Provider = when (str("kind")) {
                    "xtream" -> Provider.Xtream(
                        str("name") ?: DEFAULT_PROVIDER_NAME,
                        str("server") ?: return@forEach,
                        str("username") ?: return@forEach,
                        str("password") ?: return@forEach,
                        allow,
                    )
                    "m3u" -> Provider.M3u(str("name") ?: DEFAULT_PLAYLIST_NAME, str("url") ?: return@forEach, allow)
                    else -> return@forEach
                }
                if (SyncCrypto.providerTag(keys, p.syncIdentity) == tag) out[tag] = SharedLogin(at, p)
            }
            return out
        }
    }
}
