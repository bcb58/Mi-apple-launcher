package com.bcb58.mialauncher

import androidx.test.core.app.ApplicationProvider
import com.bcb58.mialauncher.data.AppEntry
import com.bcb58.mialauncher.data.AppRepository
import com.bcb58.mialauncher.data.AppRow
import com.bcb58.mialauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrefsAndRowsTest {

    private lateinit var prefs: Prefs

    private fun entry(name: String, tv: Boolean = true) = AppEntry(
        packageName = "com.example.$name",
        activityName = "com.example.$name.Main",
        label = name.replaceFirstChar { it.uppercase() },
        isTvApp = tv,
        hasBanner = tv,
        isSystem = false,
        versionCode = 1L
    )

    private val netflix = entry("netflix")
    private val youtube = entry("youtube")
    private val spotify = entry("spotify")
    private val calculator = entry("calculator", tv = false)
    private val all = listOf(netflix, youtube, spotify, calculator)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("mial_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = Prefs.get(context)
        prefs.favorites = emptyList()
        prefs.hiddenApps = emptySet()
    }

    private fun rows() = AppRepository.buildRows(
        apps = all,
        prefs = prefs,
        favoritesTitle = "Fav",
        recentsTitle = "Recent",
        tvTitle = "TV",
        otherTitle = "Other"
    )

    @Test
    fun `toggling a favourite adds then removes it`() {
        assertTrue(prefs.toggleFavorite(netflix.id))
        assertTrue(prefs.isFavorite(netflix.id))
        assertFalse(prefs.toggleFavorite(netflix.id))
        assertFalse(prefs.isFavorite(netflix.id))
    }

    @Test
    fun `favourites can be reordered and never run off the ends`() {
        prefs.favorites = listOf(netflix.id, youtube.id, spotify.id)

        assertTrue(prefs.moveFavorite(spotify.id, -1))
        assertEquals(listOf(netflix.id, spotify.id, youtube.id), prefs.favorites)

        // Already first: moving left is a no-op, not a crash or a wrap-around.
        assertFalse(prefs.moveFavorite(netflix.id, -1))
        assertEquals(listOf(netflix.id, spotify.id, youtube.id), prefs.favorites)

        assertFalse(prefs.moveFavorite(youtube.id, +5))
        assertEquals(listOf(netflix.id, spotify.id, youtube.id), prefs.favorites)

        // An app that is not pinned cannot be reordered.
        assertFalse(prefs.moveFavorite(calculator.id, -1))
    }

    @Test
    fun `rows split TV apps from phone apps and honour favourites order`() {
        prefs.favorites = listOf(spotify.id, netflix.id)

        val rows = rows()
        assertEquals(listOf("fav", "tv", "other"), rows.map { it.key })

        val fav = rows.first { it.key == "fav" }
        assertEquals(listOf(spotify.id, netflix.id), fav.items.map { it.id })

        // A favourite must not also appear further down the page.
        val tv = rows.first { it.key == "tv" }
        assertEquals(listOf(youtube.id), tv.items.map { it.id })

        val other = rows.first { it.key == "other" }
        assertEquals(listOf(calculator.id), other.items.map { it.id })
        assertEquals(AppRow.Style.TILE, other.style)
    }

    @Test
    fun `hidden apps disappear from every row`() {
        prefs.favorites = listOf(netflix.id)
        prefs.setHidden(netflix.id, true)
        prefs.setHidden(calculator.id, true)

        val rows = rows()
        val ids = rows.flatMap { it.items }.map { it.id }
        assertFalse(ids.contains(netflix.id))
        assertFalse(ids.contains(calculator.id))
        assertTrue(ids.contains(youtube.id))
        assertFalse(rows.any { it.key == "fav" })
    }

    @Test
    fun `recents row reflects launch order and drops when disabled`() {
        prefs.noteLaunch(youtube.id)
        Thread.sleep(5)
        prefs.noteLaunch(spotify.id)

        val recents = rows().first { it.key == "recent" }
        assertEquals(listOf(spotify.id, youtube.id), recents.items.map { it.id })

        prefs.showRecents = false
        assertFalse(rows().any { it.key == "recent" })
    }

    @Test
    fun `forgetting an app clears its history and pin`() {
        prefs.favorites = listOf(netflix.id)
        prefs.noteLaunch(netflix.id)
        prefs.forget(netflix.id)

        assertEquals(0, prefs.launchCount(netflix.id))
        assertEquals(0L, prefs.lastLaunch(netflix.id))
        assertFalse(prefs.isFavorite(netflix.id))
    }

    @Test
    fun `an empty app list produces no rows rather than empty shelves`() {
        val rows = AppRepository.buildRows(
            apps = emptyList(),
            prefs = prefs,
            favoritesTitle = "Fav",
            recentsTitle = "Recent",
            tvTitle = "TV",
            otherTitle = "Other"
        )
        assertTrue(rows.isEmpty())
    }
}
