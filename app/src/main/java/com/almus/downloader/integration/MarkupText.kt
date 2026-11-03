package com.almus.downloader.integration

/**
 * Reads plain text out of the small piece of embed markup a service returns (X puts the text of a post in the first
 * paragraph). The markup is only ever read as text: it is never shown, loaded or run, and no script, style or link in
 * it is followed.
 */
internal object MarkupText {
    private val paragraph = Regex("<p[^>]*>(.*?)</p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val lineBreak = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val tag = Regex("<[^>]*>")
    private val decimal = Regex("&#([0-9]{1,7});")
    private val hex = Regex("&#[xX]([0-9a-fA-F]{1,6});")

    /** The text of the first paragraph, or null when there is none. */
    fun firstParagraph(html: String?): String? {
        if (html == null) return null
        val inner = paragraph.find(html)?.groupValues?.get(1) ?: return null
        return plain(inner)
    }

    fun plain(fragment: String): String {
        var text = lineBreak.replace(fragment, "\n")
        text = tag.replace(text, "")
        text = decimal.replace(text) { m -> codePointText(m.groupValues[1].toIntOrNull()) ?: m.value }
        text = hex.replace(text) { m -> codePointText(m.groupValues[1].toIntOrNull(16)) ?: m.value }
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
    }

    private fun codePointText(codePoint: Int?): String? {
        if (codePoint == null) return null
        if (codePoint < 32 || codePoint > 0x10FFFF) return null
        if (codePoint in 0xD800..0xDFFF) return null
        return String(Character.toChars(codePoint))
    }
}
