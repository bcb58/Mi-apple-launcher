package com.bcb58.mialauncher.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bcb58.mialauncher.R
import com.bcb58.mialauncher.data.Prefs
import com.bcb58.mialauncher.util.Launch

/** The launcher's own settings. Deliberately short — everything is one tap. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var container: LinearLayout
    private lateinit var wallpaperView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        setContentView(R.layout.activity_settings)

        wallpaperView = findViewById(R.id.wallpaper)
        container = findViewById(R.id.items)
        render(focusIndex = 0)
    }

    private fun render(focusIndex: Int) {
        wallpaperView.setImageResource(Wallpapers.drawableFor(prefs.wallpaper))
        container.removeAllViews()

        row(
            title = getString(R.string.settings_wallpaper),
            subtitle = getString(Wallpapers.nameFor(prefs.wallpaper))
        ) {
            prefs.wallpaper = (prefs.wallpaper + 1) % Wallpapers.COUNT
            render(focusIndex = 0)
        }

        toggle(
            title = getString(R.string.settings_ambient),
            subtitle = getString(R.string.settings_ambient_desc),
            value = prefs.ambientBackdrop
        ) {
            prefs.ambientBackdrop = !prefs.ambientBackdrop
            render(focusIndex = 1)
        }

        toggle(
            title = getString(R.string.settings_parallax),
            subtitle = getString(R.string.settings_parallax_desc),
            value = prefs.parallaxFocus
        ) {
            prefs.parallaxFocus = !prefs.parallaxFocus
            render(focusIndex = 2)
        }

        toggle(
            title = getString(R.string.settings_clock),
            subtitle = null,
            value = prefs.showClock
        ) {
            prefs.showClock = !prefs.showClock
            render(focusIndex = 3)
        }

        toggle(
            title = getString(R.string.settings_recents),
            subtitle = null,
            value = prefs.showRecents
        ) {
            prefs.showRecents = !prefs.showRecents
            render(focusIndex = 4)
        }

        val hiddenCount = prefs.hiddenApps.size
        if (hiddenCount > 0) {
            row(
                title = getString(R.string.settings_unhide),
                subtitle = getString(R.string.settings_unhide_desc, hiddenCount)
            ) {
                prefs.hiddenApps = emptySet()
                render(focusIndex = 5)
            }
        }

        row(
            title = getString(R.string.settings_default_launcher),
            subtitle = getString(R.string.settings_default_launcher_desc)
        ) {
            Launch.defaultLauncherSettings(this)
        }

        container.post {
            container.getChildAt(focusIndex.coerceIn(0, container.childCount - 1))
                ?.requestFocus()
        }
    }

    private fun toggle(
        title: String,
        subtitle: String?,
        value: Boolean,
        action: () -> Unit
    ) {
        val state = getString(if (value) R.string.settings_on else R.string.settings_off)
        row(title, if (subtitle == null) state else "$state · $subtitle", action)
    }

    private fun row(title: String, subtitle: String?, action: () -> Unit) {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_setting, container, false)
        view.findViewById<TextView>(R.id.title).text = title
        val sub = view.findViewById<TextView>(R.id.subtitle)
        if (subtitle.isNullOrEmpty()) {
            sub.visibility = View.GONE
        } else {
            sub.visibility = View.VISIBLE
            sub.text = subtitle
        }
        view.setOnClickListener { action() }
        container.addView(view)
    }
}
