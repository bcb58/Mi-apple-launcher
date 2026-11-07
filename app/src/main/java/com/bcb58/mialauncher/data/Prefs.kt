package com.bcb58.mialauncher.data

import android.content.Context
import android.content.SharedPreferences

/** All user state. Tiny, synchronous reads; writes are always async. */
class Prefs private constructor(private val sp: SharedPreferences) {

    var favorites: List<String>
        get() = sp.getString(KEY_FAVORITES, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = sp.edit().putString(KEY_FAVORITES, value.joinToString("\n")).apply()

    var hiddenApps: Set<String>
        get() = sp.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_HIDDEN, value).apply()

    /** 0 = none, otherwise one of [WALLPAPERS]. */
    var wallpaper: Int
        get() = sp.getInt(KEY_WALLPAPER, 0)
        set(value) = sp.edit().putInt(KEY_WALLPAPER, value).apply()

    var ambientBackdrop: Boolean
        get() = sp.getBoolean(KEY_AMBIENT, true)
        set(value) = sp.edit().putBoolean(KEY_AMBIENT, value).apply()

    var parallaxFocus: Boolean
        get() = sp.getBoolean(KEY_PARALLAX, true)
        set(value) = sp.edit().putBoolean(KEY_PARALLAX, value).apply()

    var showClock: Boolean
        get() = sp.getBoolean(KEY_CLOCK, true)
        set(value) = sp.edit().putBoolean(KEY_CLOCK, value).apply()

    var showRecents: Boolean
        get() = sp.getBoolean(KEY_RECENTS, true)
        set(value) = sp.edit().putBoolean(KEY_RECENTS, value).apply()

    fun isFavorite(id: String) = favorites.contains(id)

    fun toggleFavorite(id: String): Boolean {
        val current = favorites.toMutableList()
        val added = if (current.remove(id)) false else { current.add(id); true }
        favorites = current
        return added
    }

    fun moveFavorite(id: String, delta: Int): Boolean {
        val current = favorites.toMutableList()
        val from = current.indexOf(id)
        if (from < 0) return false
        val to = (from + delta).coerceIn(0, current.size - 1)
        if (to == from) return false
        current.removeAt(from)
        current.add(to, id)
        favorites = current
        return true
    }

    fun setHidden(id: String, hidden: Boolean) {
        val next = hiddenApps.toMutableSet()
        if (hidden) next.add(id) else next.remove(id)
        hiddenApps = next
    }

    /** Own usage counter, so "Son Kullanılanlar" needs no special permission. */
    fun noteLaunch(id: String) {
        val now = System.currentTimeMillis()
        sp.edit()
            .putInt(KEY_COUNT_PREFIX + id, launchCount(id) + 1)
            .putLong(KEY_LAST_PREFIX + id, now)
            .apply()
    }

    fun launchCount(id: String): Int = sp.getInt(KEY_COUNT_PREFIX + id, 0)

    fun lastLaunch(id: String): Long = sp.getLong(KEY_LAST_PREFIX + id, 0L)

    fun forget(id: String) {
        sp.edit()
            .remove(KEY_COUNT_PREFIX + id)
            .remove(KEY_LAST_PREFIX + id)
            .apply()
        favorites = favorites.filterNot { it == id }
    }

    companion object {
        private const val FILE = "mial_prefs"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_WALLPAPER = "wallpaper"
        private const val KEY_AMBIENT = "ambient"
        private const val KEY_PARALLAX = "parallax"
        private const val KEY_CLOCK = "clock"
        private const val KEY_RECENTS = "recents"
        private const val KEY_COUNT_PREFIX = "c:"
        private const val KEY_LAST_PREFIX = "t:"

        /** Built-in gradients; index 0 means "plain black". */
        val WALLPAPERS = intArrayOf(0, 1, 2, 3, 4)

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(
                context.applicationContext
                    .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
