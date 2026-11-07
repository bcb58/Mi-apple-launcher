package com.bcb58.mialauncher.ui

import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcb58.mialauncher.R
import com.bcb58.mialauncher.data.AppEntry
import com.bcb58.mialauncher.data.AppRepository
import com.bcb58.mialauncher.data.AppRow
import com.bcb58.mialauncher.data.Prefs
import com.bcb58.mialauncher.util.Launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Every installed app, alphabetical, in a focus-scrolling grid. */
class AllAppsActivity : AppCompatActivity() {

    private lateinit var grid: RecyclerView
    private lateinit var adapter: AppCardAdapter
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        setContentView(R.layout.activity_all_apps)

        findViewById<ImageView>(R.id.wallpaper)
            .setImageResource(Wallpapers.drawableFor(prefs.wallpaper))

        grid = findViewById(R.id.grid)
        adapter = AppCardAdapter(
            context = this,
            style = AppRow.Style.BANNER,
            onClick = { entry, view -> Launch.app(this, entry, view) },
            onLongClick = { entry, _ ->
                AppMenuDialog(this, entry) { refresh() }.show()
            },
            onFocus = { _, _ -> }
        )

        grid.layoutManager = object : GridLayoutManager(this, columnCount()) {
            override fun getExtraLayoutSpace(state: RecyclerView.State) = height / 2
        }
        grid.setHasFixedSize(true)
        grid.itemAnimator = null
        grid.setItemViewCacheSize(8)
        grid.adapter = adapter
        grid.addItemDecoration(VerticalGapDecoration(
            resources.getDimensionPixelSize(R.dimen.row_gap)
        ))

        refresh()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppRepository.apps.collectLatest { submit(it) }
            }
        }
        grid.post { grid.getChildAt(0)?.findViewById<View>(R.id.card)?.requestFocus() }
    }

    private fun columnCount(): Int {
        val usable = resources.displayMetrics.widthPixels -
            2 * resources.getDimensionPixelSize(R.dimen.overscan_horizontal)
        val cell = resources.getDimensionPixelSize(R.dimen.card_banner_width) +
            resources.getDimensionPixelSize(R.dimen.card_gap)
        return (usable / cell).coerceIn(3, 8)
    }

    private fun refresh() = submit(AppRepository.apps.value.ifEmpty { AppRepository.snapshot(this) })

    private fun submit(apps: List<AppEntry>) {
        val hidden = prefs.hiddenApps
        adapter.submit(apps.filterNot { hidden.contains(it.id) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            FocusDirection.last = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> FocusDirection.UP
                KeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.DOWN
                else -> FocusDirection.NONE
            }
            if (event.keyCode == KeyEvent.KEYCODE_MENU) {
                (currentFocus as? FocusCardView)?.performLongClick()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private class VerticalGapDecoration(private val gap: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            outRect.bottom = gap
        }
    }
}
