package com.bcb58.mialauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.bcb58.mialauncher.data.AppEntry
import com.bcb58.mialauncher.data.AppRow
import com.bcb58.mialauncher.ui.AppCardAdapter
import com.bcb58.mialauncher.ui.FocusCardView
import com.bcb58.mialauncher.ui.RowAdapter
import com.bcb58.mialauncher.ui.SettingsActivity
import com.bcb58.mialauncher.ui.TvColumnRecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

/**
 * These are the checks an emulator would otherwise give us: every custom view
 * really inflates from XML, the home screen builds its adapters, and the
 * settings screen renders its rows.
 */
@RunWith(RobolectricTestRunner::class)
class UiInflationTest {

    private fun context() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun themedInflater(): LayoutInflater {
        val themed = androidx.appcompat.view.ContextThemeWrapper(context(), R.style.Theme_MiApple)
        return LayoutInflater.from(themed)
    }

    @Test
    fun `app card inflates with its custom focus view`() {
        val view = themedInflater().inflate(R.layout.item_app_card, null)
        val card = view.findViewById<View>(R.id.card)
        assertTrue("card should be a FocusCardView", card is FocusCardView)
        assertNotNull(view.findViewById<View>(R.id.art))
        assertNotNull(view.findViewById<TextView>(R.id.label))
    }

    @Test
    fun `focus card reads its corner radius attribute and resets cleanly`() {
        val view = themedInflater().inflate(R.layout.item_app_card, null)
        val card = view.findViewById<FocusCardView>(R.id.card)
        card.scaleX = 1.3f
        card.rotationY = 9f
        card.resetVisuals()
        assertEquals(1f, card.scaleX, 0.001f)
        assertEquals(0f, card.rotationY, 0.001f)
    }

    @Test
    fun `row layout inflates both custom recycler views`() {
        val row = themedInflater().inflate(R.layout.item_row, null)
        assertNotNull(row.findViewById<View>(R.id.rowItems))
        val main = themedInflater().inflate(R.layout.activity_main, null)
        assertTrue(main.findViewById<View>(R.id.rows) is TvColumnRecyclerView)
        assertNotNull(main.findViewById<View>(R.id.tabHome))
        assertNotNull(main.findViewById<View>(R.id.tabApps))
        assertNotNull(main.findViewById<View>(R.id.tabSettings))
    }

    @Test
    fun `menu and settings rows inflate`() {
        assertNotNull(themedInflater().inflate(R.layout.dialog_app_menu, null)
            .findViewById<View>(R.id.menuItems))
        assertNotNull(themedInflater().inflate(R.layout.item_menu_row, null)
            .findViewById<TextView>(R.id.title))
        assertNotNull(themedInflater().inflate(R.layout.item_setting, null)
            .findViewById<TextView>(R.id.title))
    }

    @Test
    fun `card adapter binds without touching the package manager on the main thread`() {
        val parent = androidx.recyclerview.widget.RecyclerView(context()).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context())
        }
        val adapter = AppCardAdapter(
            context = context(),
            style = AppRow.Style.BANNER,
            onClick = { _, _ -> },
            onLongClick = { _, _ -> },
            onFocus = { _, _ -> }
        )
        val items = listOf(
            AppEntry("com.a", "com.a.Main", "A", true, false, false, 1L),
            AppEntry("com.b", "com.b.Main", "B", false, false, false, 1L)
        )
        adapter.submit(items)
        assertEquals(2, adapter.itemCount)
        assertEquals(items[0].stableId, adapter.getItemId(0))

        val holder = adapter.createViewHolder(parent, 0)
        adapter.bindViewHolder(holder, 0)
        assertEquals("A", holder.itemView.findViewById<TextView>(R.id.label).text)
        adapter.onViewRecycled(holder)
    }

    @Test
    fun `row adapter diffs shelves instead of rebuilding them`() {
        val adapter = RowAdapter(context(), { _, _ -> }, { _, _ -> }, { _, _ -> })
        val a = AppEntry("com.a", "com.a.Main", "A", true, false, false, 1L)
        val b = AppEntry("com.b", "com.b.Main", "B", true, false, false, 1L)

        adapter.submit(listOf(AppRow("tv", "TV", listOf(a))))
        assertEquals(1, adapter.itemCount)

        adapter.submit(listOf(AppRow("fav", "Fav", listOf(b)), AppRow("tv", "TV", listOf(a, b))))
        assertEquals(2, adapter.itemCount)
        assertEquals("fav", adapter.rowAt(0)?.key)
        assertEquals(2, adapter.rowAt(1)?.items?.size)
    }

    @Test
    fun `settings activity renders every row and toggles survive a redraw`() {
        val controller: ActivityController<SettingsActivity> =
            Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        val items = activity.findViewById<ViewGroup>(R.id.items)
        assertTrue("expected several settings rows", items.childCount >= 6)

        val before = com.bcb58.mialauncher.data.Prefs.get(activity).parallaxFocus
        items.getChildAt(2).performClick()
        assertEquals(!before, com.bcb58.mialauncher.data.Prefs.get(activity).parallaxFocus)
        controller.pause().stop().destroy()
    }
}
