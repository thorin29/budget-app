package com.budget.app.data

/**
 * The record screen's number pad. The amount starts at the planned figure; the
 * first digit typed replaces it ("fresh"), after which digits append. Pure, so
 * it is unit-tested.
 */
object AmountInput {
    const val DELETE = '<'
    private const val MAX_WHOLE_DIGITS = 7

    /** The text after pressing [key] ('0'–'9', '.', or [DELETE]). */
    fun press(text: String, key: Char, fresh: Boolean): String {
        val current = if (fresh) "" else text
        return when (key) {
            DELETE -> if (fresh) "" else current.dropLast(1)
            '.' -> if (current.contains('.')) current else (current.ifEmpty { "0" }) + "."
            in '0'..'9' -> {
                val dot = current.indexOf('.')
                when {
                    dot >= 0 && current.length - dot - 1 >= 2 -> current
                    dot < 0 && current.length >= MAX_WHOLE_DIGITS -> current
                    current == "0" -> key.toString()
                    else -> current + key
                }
            }
            else -> current
        }
    }

    /** "1234.5" → "1,234.5", for showing while typing. Empty shows "0". */
    fun display(text: String): String {
        if (text.isEmpty()) return "0"
        val dot = text.indexOf('.')
        val whole = if (dot >= 0) text.substring(0, dot) else text
        val rest = if (dot >= 0) text.substring(dot) else ""
        val grouped = whole.ifEmpty { "0" }.reversed().chunked(3).joinToString(",").reversed()
        return grouped + rest
    }

    /** Cents for the typed text, or null while it isn't a complete amount. */
    fun cents(text: String): Long? = if (text.isEmpty()) null else Money.parse(text.removeSuffix("."))
}
