package io.github.andrepg.shared.text

/**
 * Shared character-escaping utilities.
 *
 * Collapses the duplicated char-by-char `when` escape tables into one lookup
 * helper. JDK-only.
 */
object EscapeTables {
    /**
     * Escapes [value] char by char: characters found in [table] are replaced by
     * their mapped string; otherwise [controlChar] is consulted (for e.g. JSON
     * control-char escapes); unmatched characters pass through unchanged.
     */
    fun escape(
        value: CharSequence,
        table: Map<Char, String>,
        controlChar: (Char) -> String? = { null },
    ): String {
        if (value.isEmpty()) return value.toString()
        return buildString(value.length) {
            for (ch in value) {
                val escaped = table[ch] ?: controlChar(ch)
                if (escaped != null) append(escaped) else append(ch)
            }
        }
    }

    /** JSON string escaping: quote/backslash escapes plus `\uXXXX` control chars. */
    private val JSON_ESCAPES: Map<Char, String> =
        mapOf(
            '"' to "\\\"",
            '\\' to "\\\\",
            '\n' to "\\n",
            '\r' to "\\r",
            '\t' to "\\t",
            '\b' to "\\b",
            '\u000C' to "\\f",
        )

    /** Escapes [value] for embedding inside a JSON string literal. */
    fun json(value: CharSequence): String =
        escape(value, JSON_ESCAPES) { ch ->
            if (ch.code < 0x20) "\\u%04x".format(ch.code) else null
        }

    /** XML character escaping for attribute values. */
    private val XML_ESCAPES: Map<Char, String> =
        mapOf(
            '&' to "&amp;",
            '<' to "&lt;",
            '>' to "&gt;",
            '"' to "&quot;",
            '\'' to "&apos;",
        )

    /** Escapes [value] for embedding inside an XML attribute value. */
    fun xml(value: CharSequence): String = escape(value, XML_ESCAPES)
}
