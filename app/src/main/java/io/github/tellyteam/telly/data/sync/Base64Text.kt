package io.github.tellyteam.telly.data.sync

/** Base64 without android.util (so it runs in unit tests) or java.util.Base64 (API 26; Fire OS 6 is 25). */
internal object Base64Text {
    private const val STANDARD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /** Standard with padding, or [url]-safe without. */
    fun encode(bytes: ByteArray, url: Boolean = false): String {
        val alphabet = if (url) URL else STANDARD
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val n = minOf(3, bytes.size - i)
            var chunk = 0
            for (j in 0 until 3) chunk = (chunk shl 8) or (if (j < n) bytes[i + j].toInt() and 0xff else 0)
            for (j in 0..n) out.append(alphabet[(chunk ushr (18 - 6 * j)) and 63])
            if (!url) repeat(3 - n) { out.append('=') }
            i += 3
        }
        return out.toString()
    }

    /** Either alphabet, padded or not; null if it isn't base64. */
    fun decode(text: String): ByteArray? {
        val clean = text.trimEnd('=')
        val out = java.io.ByteArrayOutputStream(clean.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val v = when (c) {
                in 'A'..'Z' -> c - 'A'
                in 'a'..'z' -> c - 'a' + 26
                in '0'..'9' -> c - '0' + 52
                '+', '-' -> 62
                '/', '_' -> 63
                else -> return null
            }
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer ushr bits) and 0xff)
            }
        }
        return out.toByteArray()
    }
}
