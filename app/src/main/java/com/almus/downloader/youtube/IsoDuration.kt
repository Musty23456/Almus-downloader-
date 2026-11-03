package com.almus.downloader.youtube

/** Reads the ISO 8601 durations the YouTube Data API uses ("PT3M32S") and writes them for people ("3:32"). */
object IsoDuration {
    private val pattern = Regex("^P(?:(\\d{1,6})D)?(?:T(?:(\\d{1,6})H)?(?:(\\d{1,6})M)?(?:(\\d{1,6})S)?)?$")

    /** Whole seconds, or null when [text] is not a valid duration. */
    fun parseSeconds(text: String?): Long? {
        val t = text?.trim() ?: return null
        if (t == "P" || t.endsWith("T")) return null
        val m = pattern.matchEntire(t) ?: return null
        fun part(n: Int): Long = m.groupValues[n].toLongOrNull() ?: 0L
        return part(1) * 86_400L + part(2) * 3_600L + part(3) * 60L + part(4)
    }

    /** "3:32" or "1:02:03". Negative values are shown as 0:00. */
    fun format(totalSeconds: Long): String {
        val total = if (totalSeconds < 0) 0L else totalSeconds
        val h = total / 3_600L
        val m = (total % 3_600L) / 60L
        val s = total % 60L
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
    }
}
