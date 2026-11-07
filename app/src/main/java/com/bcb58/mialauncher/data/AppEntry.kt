package com.bcb58.mialauncher.data

/**
 * One launchable activity. Deliberately flat and primitive-only so the whole
 * list can be written to / read from a single text file in a few milliseconds,
 * which is what makes the cold start feel instant on the Mi Box's eMMC.
 */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    /** Declares LEANBACK_LAUNCHER, i.e. it was built for TV. */
    val isTvApp: Boolean,
    /** Ships an android:banner, so we can use the real 16:9 artwork. */
    val hasBanner: Boolean,
    val isSystem: Boolean,
    val versionCode: Long
) {
    val id: String get() = "$packageName/$activityName"

    val stableId: Long
        get() {
            var h = 1125899906842597L
            for (c in id) h = 31 * h + c.code
            return h
        }

    fun toLine(): String = listOf(
        packageName,
        activityName,
        label.replace('\t', ' ').replace('\n', ' '),
        if (isTvApp) "1" else "0",
        if (hasBanner) "1" else "0",
        if (isSystem) "1" else "0",
        versionCode.toString()
    ).joinToString("\t")

    companion object {
        fun fromLine(line: String): AppEntry? {
            val p = line.split('\t')
            if (p.size < 7) return null
            return AppEntry(
                packageName = p[0],
                activityName = p[1],
                label = p[2],
                isTvApp = p[3] == "1",
                hasBanner = p[4] == "1",
                isSystem = p[5] == "1",
                versionCode = p[6].toLongOrNull() ?: 0L
            )
        }
    }
}

/** A titled horizontal strip on the home screen. */
data class AppRow(
    val key: String,
    val title: String,
    val items: List<AppEntry>,
    val style: Style = Style.BANNER
) {
    enum class Style { BANNER, TILE }
}
