package com.bcb58.mialauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bcb58.mialauncher.data.AppEntry
import com.bcb58.mialauncher.data.AppRepository
import com.bcb58.mialauncher.data.AppRow
import com.bcb58.mialauncher.data.Artwork
import com.bcb58.mialauncher.data.PackageWatcher
import com.bcb58.mialauncher.data.Prefs
import com.bcb58.mialauncher.ui.AppMenuDialog
import com.bcb58.mialauncher.ui.AllAppsActivity
import com.bcb58.mialauncher.ui.FocusCardView
import com.bcb58.mialauncher.ui.FocusDirection
import com.bcb58.mialauncher.ui.Motion
import com.bcb58.mialauncher.ui.RowAdapter
import com.bcb58.mialauncher.ui.SettingsActivity
import com.bcb58.mialauncher.ui.TvColumnRecyclerView
import com.bcb58.mialauncher.ui.Wallpapers
import com.bcb58.mialauncher.util.Launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var rows: TvColumnRecyclerView
    private lateinit var rowAdapter: RowAdapter
    private lateinit var wallpaper: ImageView
    private lateinit var backdrop: ImageView
    private lateinit var backdropScrim: View
    private lateinit var clock: TextView
    private lateinit var emptyState: View
    private lateinit var tabHome: TextView
    private lateinit var tabApps: TextView
    private lateinit var tabSettings: TextView

    private var clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var currentBackdropKey: String? = null
    private var pendingFocusRestore = true

    private val timeTicker = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateClock()
    }

    private val appsChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppRepository.requestRefresh(context)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        setContentView(R.layout.activity_main)

        wallpaper = findViewById(R.id.wallpaper)
        backdrop = findViewById(R.id.backdrop)
        backdropScrim = findViewById(R.id.backdropScrim)
        rows = findViewById(R.id.rows)
        emptyState = findViewById(R.id.emptyState)
        clock = findViewById(R.id.clock)
        tabHome = findViewById(R.id.tabHome)
        tabApps = findViewById(R.id.tabApps)
        tabSettings = findViewById(R.id.tabSettings)

        rowAdapter = RowAdapter(
            context = this,
            onClick = ::openApp,
            onLongClick = ::showAppMenu,
            onFocus = ::onCardFocused
        )
        rows.adapter = rowAdapter

        setupTabs()
        applyWallpaper()
        updateClock()

        // Paint from the on-disk cache before the first coroutine even runs.
        renderRows(AppRepository.snapshot(this))

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppRepository.apps.collectLatest { renderRows(it) }
            }
        }
    }

    // ------------------------------------------------------------- top bar

    private fun setupTabs() {
        tabHome.isSelected = true
        tabHome.setOnClickListener { scrollToTop() }
        tabApps.setOnClickListener {
            startActivity(Intent(this, AllAppsActivity::class.java))
        }
        tabSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        // A long press on Settings jumps straight to the system settings,
        // which is the one thing people hunt for on a replacement launcher.
        tabSettings.setOnLongClickListener {
            Launch.systemSettings(this)
            true
        }
    }

    private fun updateClock() {
        if (!prefs.showClock) {
            clock.visibility = View.GONE
            return
        }
        clock.visibility = View.VISIBLE
        clock.text = clockFormat.format(Date())
    }

    private fun applyWallpaper() {
        wallpaper.setImageResource(Wallpapers.drawableFor(prefs.wallpaper))
    }

    // --------------------------------------------------------------- rows

    private fun renderRows(apps: List<AppEntry>) {
        val built = AppRepository.buildRows(
            apps = apps,
            prefs = prefs,
            favoritesTitle = getString(R.string.row_favorites),
            recentsTitle = getString(R.string.row_recents),
            tvTitle = getString(R.string.row_tv_apps),
            otherTitle = getString(R.string.row_other_apps)
        )
        emptyState.visibility = if (built.isEmpty()) View.VISIBLE else View.GONE
        rowAdapter.submit(built)

        if (pendingFocusRestore && built.isNotEmpty()) {
            pendingFocusRestore = false
            rows.post { focusFirstCard() }
        }
    }

    private fun focusFirstCard(): Boolean {
        val firstRow = rows.getChildAt(0) ?: return false
        val card = findFirstCard(firstRow) ?: return false
        return card.requestFocus()
    }

    private fun findFirstCard(view: View): View? {
        if (view is FocusCardView && view.isFocusable) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findFirstCard(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun scrollToTop() {
        rows.smoothScrollToPosition(0)
        rows.postDelayed({ focusFirstCard() }, Motion.ROW_SCROLL_MS)
    }

    // ------------------------------------------------------------- actions

    private fun openApp(entry: AppEntry, from: View) {
        Launch.app(this, entry, from)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showAppMenu(entry: AppEntry, from: View) {
        AppMenuDialog(this, entry) { AppRepository.requestRefresh(this) }.show()
    }

    /** Bleeds the focused card's colours into the background, tvOS style. */
    private fun onCardFocused(entry: AppEntry, card: View) {
        if (!prefs.ambientBackdrop) return
        val key = Artwork.keyFor(entry, card.width, card.height)
        if (key == currentBackdropKey) return
        currentBackdropKey = key

        val source: Bitmap = Artwork.cached(key) ?: return
        lifecycleScope.launch {
            val blurred = withContext(Dispatchers.Default) {
                runCatching { Artwork.backdrop(key, source) }.getOrNull()
            } ?: return@launch
            if (currentBackdropKey != key) return@launch
            crossfadeBackdrop(blurred)
        }
    }

    private fun crossfadeBackdrop(bitmap: Bitmap) {
        backdrop.animate().cancel()
        backdrop.animate().alpha(0f).setDuration(Motion.BACKDROP_MS / 2).withEndAction {
            backdrop.setImageBitmap(bitmap)
            backdrop.animate()
                .alpha(0.38f)
                .setDuration(Motion.BACKDROP_MS)
                .setInterpolator(Motion.standard)
                .start()
            backdropScrim.animate().alpha(1f).setDuration(Motion.BACKDROP_MS).start()
        }.start()
    }

    // ---------------------------------------------------------------- keys

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            FocusDirection.last = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> FocusDirection.UP
                KeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.DOWN
                else -> FocusDirection.NONE
            }
            // The remote's menu button opens the card's context sheet, which
            // is where a TV user expects "uninstall" to live.
            if (event.keyCode == KeyEvent.KEYCODE_MENU) {
                (currentFocus as? FocusCardView)?.performLongClick()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Kept for API < 33; onBackInvokedCallback is not needed for a home app")
    @Suppress("DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() {
        // A launcher must never finish itself on Back.
        scrollToTop()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // HOME pressed while already home: return to the top, like tvOS.
        scrollToTop()
    }

    // --------------------------------------------------------------- state

    override fun onStart() {
        super.onStart()
        registerReceiver(timeTicker, IntentFilter(Intent.ACTION_TIME_TICK))
        val filter = IntentFilter(PackageWatcher.ACTION_APPS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(appsChanged, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(appsChanged, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        runCatching { unregisterReceiver(timeTicker) }
        runCatching { unregisterReceiver(appsChanged) }
    }

    override fun onResume() {
        super.onResume()
        clockFormat = SimpleDateFormat(
            if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a",
            Locale.getDefault()
        )
        updateClock()
        applyWallpaper()
        // Favourites, hidden apps and the recents row can all have changed
        // while we were away.
        renderRows(AppRepository.apps.value.ifEmpty { AppRepository.snapshot(this) })
        AppRepository.requestRefresh(this)
    }
}
