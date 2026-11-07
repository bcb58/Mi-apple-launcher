package com.bcb58.mialauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.bcb58.mialauncher.data.AppEntry
import com.bcb58.mialauncher.data.AppRow
import com.bcb58.mialauncher.data.Artwork
import com.bcb58.mialauncher.ui.AppCardAdapter
import com.bcb58.mialauncher.ui.FocusCardView
import com.bcb58.mialauncher.ui.RowAdapter
import com.bcb58.mialauncher.ui.TvColumnRecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real home screen to a PNG with Robolectric's native graphics
 * pipeline. This is the closest thing to running the launcher on a TV that
 * works without a device, and it catches layout regressions that inflation
 * tests cannot see — clipped cards, collapsed rows, invisible labels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenRenderTest {

    private val width = 1920
    private val height = 1080

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    /**
     * Registers fake installed apps whose icons resolve against *our* resources,
     * so the card compositor produces genuinely different accent colours.
     */
    private fun fakeApps(): List<AppEntry> {
        val ctx = context()
        val pm = shadowOf(ctx.packageManager)
        val icons = listOf(
            R.mipmap.ic_launcher,
            R.drawable.ic_star,
            R.drawable.ic_apps,
            R.drawable.ic_home,
            R.drawable.ic_settings,
            R.drawable.ic_info
        )
        val names = listOf(
            "Netflix", "YouTube", "Spotify", "Prime Video", "Disney+", "Plex",
            "BluTV", "Exxen", "TOD", "Twitch"
        )
        return names.mapIndexed { index, label ->
            val pkg = "com.example.${label.lowercase().filter { it.isLetter() }}"
            val component = ComponentName(pkg, "$pkg.MainActivity")
            val isTv = index % 4 != 3
            val info = ActivityInfo().apply {
                packageName = pkg
                name = component.className
                nonLocalizedLabel = label
                exported = true
                enabled = true
                icon = icons[index % icons.size]
                // Its own identity, or Robolectric reports our package and the
                // repository filters it out as "self" — but pointed at this
                // APK's resources so the icon really decodes.
                applicationInfo = android.content.pm.ApplicationInfo().apply {
                    this.packageName = pkg
                    sourceDir = ctx.applicationInfo.sourceDir
                    publicSourceDir = ctx.applicationInfo.publicSourceDir
                    dataDir = ctx.applicationInfo.dataDir
                    this.icon = icons[index % icons.size]
                    flags = 0
                }
            }
            pm.addOrUpdateActivity(info)
            // Register the launcher intent filter too, so AppRepository finds
            // these through the same queryIntentActivities path it uses on a TV.
            pm.addIntentFilterForActivity(
                component,
                IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addCategory(
                        if (isTv) Intent.CATEGORY_LEANBACK_LAUNCHER
                        else Intent.CATEGORY_LAUNCHER
                    )
                }
            )
            AppEntry(
                packageName = pkg,
                activityName = component.className,
                label = label,
                isTvApp = isTv,
                hasBanner = false,
                isSystem = false,
                versionCode = 1L
            )
        }
    }

    private fun themed(): Context = ContextThemeWrapper(context(), R.style.Theme_MiApple)

    /** Waits for the repository's background PackageManager query to land. */
    private fun awaitAppList() {
        com.bcb58.mialauncher.data.AppRepository.requestRefresh(context())
        val deadline = System.currentTimeMillis() + 10_000
        while (com.bcb58.mialauncher.data.AppRepository.apps.value.isEmpty() &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(20)
        }
        assertTrue(
            "repository never saw the fake apps",
            com.bcb58.mialauncher.data.AppRepository.apps.value.isNotEmpty()
        )
    }

    /** Warms the artwork cache so binding takes the synchronous path. */
    private fun warm(apps: List<AppEntry>, w: Int, h: Int) {
        for (app in apps) Artwork.loadBlocking(context(), app, w, h)
    }

    private fun render(view: View, file: File): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    @Test
    fun `the real home activity starts and renders its shelves`() {
        val apps = fakeApps()
        val res = context().resources
        val cw = res.getDimensionPixelSize(R.dimen.card_banner_width)
        val ch = res.getDimensionPixelSize(R.dimen.card_banner_height)
        warm(apps, cw, ch)
        warm(apps, res.getDimensionPixelSize(R.dimen.card_tile_width),
            res.getDimensionPixelSize(R.dimen.card_tile_height))

        // Pin a few so the launcher has a Favourites shelf to draw.
        val prefs = com.bcb58.mialauncher.data.Prefs.get(context())
        prefs.favorites = apps.take(4).map { it.id }
        apps.drop(4).take(4).forEach { prefs.noteLaunch(it.id) }

        // Let the repository discover them through PackageManager, exactly as
        // it does on a real boot, before the activity asks for a snapshot.
        awaitAppList()

        // Boot MainActivity for real, so AppCompat inflates the tabs and
        // onCreate/onResume run exactly as they do on the device.
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val root = activity.findViewById<View>(R.id.root)
        val rows = activity.findViewById<TvColumnRecyclerView>(R.id.rows)
        activity.findViewById<TextView>(R.id.clock).text = "20:45"

        val bitmap = render(root, File("build/screenshots/home.png"))

        // Shelves must actually be laid out, not collapsed to zero height.
        assertTrue("expected at least two shelves, got ${rows.childCount}", rows.childCount >= 2)
        for (i in 0 until rows.childCount) {
            val shelf = rows.getChildAt(i)
            assertTrue("shelf $i has no height", shelf.height > 100)
            val strip = shelf.findViewById<RecyclerView>(R.id.rowItems)
            assertTrue("shelf $i rendered no cards", strip.childCount >= 3)
        }
        // The top bar must carry its three tabs, with their icons attached.
        val tabs = listOf(R.id.tabHome, R.id.tabApps, R.id.tabSettings)
            .map { activity.findViewById<TextView>(it) }
        for (tab in tabs) {
            assertTrue("tab '${tab.text}' has no label", tab.text.isNotEmpty())
            assertTrue("tab '${tab.text}' is not focusable", tab.isFocusable)
            assertNotNull(
                "tab '${tab.text}' lost its icon",
                androidx.core.widget.TextViewCompat
                    .getCompoundDrawablesRelative(tab)[0]
            )
        }
        assertTrue("Home tab should start selected", tabs[0].isSelected)

        // The interface is Turkish regardless of the device locale.
        assertEquals("Ana Ekran", tabs[0].text.toString())
        assertEquals("Uygulamalar", tabs[1].text.toString())
        assertEquals("Ayarlar", tabs[2].text.toString())
        val shelfTitles = (0 until rows.childCount).map {
            rows.getChildAt(it).findViewById<TextView>(R.id.rowTitle).text.toString()
        }
        assertTrue("shelves are not Turkish: $shelfTitles",
            shelfTitles.contains("Favoriler"))

        // Focus must land on a card, not nowhere, so the remote works at once.
        assertTrue(
            "nothing took focus on start",
            activity.currentFocus is com.bcb58.mialauncher.ui.FocusCardView ||
                rows.findFocus() != null
        )

        // And the page must not be a flat colour.
        assertTrue("render looks empty", distinctColours(bitmap) > 400)
        controller.pause().stop().destroy()
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `focused card is larger and lit compared to its neighbours`() {
        val apps = fakeApps()
        val res = context().resources
        val cw = res.getDimensionPixelSize(R.dimen.card_banner_width)
        val ch = res.getDimensionPixelSize(R.dimen.card_banner_height)
        warm(apps, cw, ch)

        val container = android.widget.FrameLayout(themed()).apply {
            setBackgroundResource(R.drawable.wall_2)
            clipChildren = false
        }
        val strip = androidx.recyclerview.widget.RecyclerView(themed()).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                themed(), RecyclerView.HORIZONTAL, false
            )
            clipChildren = false
            clipToPadding = false
            setPadding(96, 56, 96, 56)
            adapter = AppCardAdapter(themed(), AppRow.Style.BANNER,
                { _, _ -> }, { _, _ -> }, { _, _ -> }
            ).also { it.submit(apps.take(5)) }
        }
        container.addView(
            strip,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 360)
        )

        container.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, width, 360)

        val item = strip.getChildAt(1)
        val second = item.findViewById<FocusCardView>(R.id.card)
        val label = item.findViewById<android.widget.TextView>(R.id.label)
        val labelTopBefore = label.top

        second.isFocusableInTouchMode = true
        second.requestFocus()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(
            java.time.Duration.ofMillis(900)
        )
        assertTrue("card did not take focus", second.isFocused)
        assertTrue(
            "focused card should be scaled up, was ${second.scaleX}",
            second.scaleX > 1.05f
        )

        // Regression: focusing must not reorder the item's children. A
        // bringToFront() here moved the label above the artwork.
        assertEquals("focus moved the label", labelTopBefore, label.top)
        assertTrue(
            "label must stay below the card (label.top=${label.top}, card.bottom=${second.bottom})",
            label.top >= second.bottom
        )
        assertEquals(
            "card must remain the first child of its item",
            0,
            (item as ViewGroup).indexOfChild(second)
        )

        val bitmap = Bitmap.createBitmap(width, 360, Bitmap.Config.ARGB_8888)
        container.draw(Canvas(bitmap))
        File("build/screenshots").mkdirs()
        File("build/screenshots/focus.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun distinctColours(bitmap: Bitmap): Int {
        val seen = HashSet<Int>()
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                seen.add(bitmap.getPixel(x, y))
                if (seen.size > 4000) return seen.size
                x += 7
            }
            y += 7
        }
        return seen.size
    }
}
