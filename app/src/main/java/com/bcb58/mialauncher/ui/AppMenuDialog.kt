package com.bcb58.mialauncher.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.bcb58.mialauncher.R
import com.bcb58.mialauncher.data.AppEntry
import com.bcb58.mialauncher.data.Artwork
import com.bcb58.mialauncher.data.Prefs
import com.bcb58.mialauncher.util.Launch
import kotlin.math.roundToInt

/**
 * The long-press / MENU sheet for a card: favourite, reorder, hide, app info,
 * uninstall. Built from plain views rather than an AlertDialog so the focus
 * highlight matches the rest of the launcher.
 */
class AppMenuDialog(
    private val activity: Activity,
    private val entry: AppEntry,
    private val onChanged: () -> Unit
) : Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar) {

    private val prefs = Prefs.get(activity)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val content = LayoutInflater.from(context).inflate(R.layout.dialog_app_menu, null)
        setContentView(content)
        window?.setBackgroundDrawable(ColorDrawable(Color.argb(190, 0, 0, 0)))
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        content.findViewById<TextView>(R.id.headerTitle).text = entry.label

        val headerArt = content.findViewById<ImageView>(R.id.headerArt)
        val density = context.resources.displayMetrics.density
        val w = (96 * density).roundToInt()
        val h = (54 * density).roundToInt()
        Artwork.cached(Artwork.keyFor(entry, w, h))?.let(headerArt::setImageBitmap)

        val items = content.findViewById<LinearLayout>(R.id.menuItems)
        buildMenu(items)
        items.getChildAt(0)?.requestFocus()
    }

    private fun buildMenu(container: LinearLayout) {
        val isFavorite = prefs.isFavorite(entry.id)

        addRow(container, R.string.menu_open, R.drawable.ic_open) {
            dismiss()
            Launch.app(activity, entry, null)
        }

        addRow(
            container,
            if (isFavorite) R.string.menu_remove_favorite else R.string.menu_add_favorite,
            if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star
        ) {
            val added = prefs.toggleFavorite(entry.id)
            Launch.toast(
                activity,
                if (added) R.string.toast_favorite_added else R.string.toast_favorite_removed
            )
            dismiss()
            onChanged()
        }

        if (isFavorite) {
            addRow(container, R.string.menu_move_left, R.drawable.ic_arrow_left) {
                prefs.moveFavorite(entry.id, -1)
                dismiss()
                onChanged()
            }
            addRow(container, R.string.menu_move_right, R.drawable.ic_arrow_right) {
                prefs.moveFavorite(entry.id, +1)
                dismiss()
                onChanged()
            }
        }

        addRow(container, R.string.menu_hide, R.drawable.ic_eye_off) {
            prefs.setHidden(entry.id, true)
            Launch.toast(activity, R.string.toast_hidden)
            dismiss()
            onChanged()
        }

        addRow(container, R.string.menu_app_info, R.drawable.ic_info) {
            dismiss()
            Launch.appInfo(activity, entry.packageName)
        }

        if (!entry.isSystem) {
            addRow(container, R.string.menu_uninstall, R.drawable.ic_trash) {
                dismiss()
                Launch.uninstall(activity, entry.packageName)
            }
        }
    }

    private fun addRow(
        container: LinearLayout,
        @StringRes title: Int,
        @DrawableRes icon: Int,
        action: () -> Unit
    ) {
        val row = LayoutInflater.from(context)
            .inflate(R.layout.item_menu_row, container, false)
        row.findViewById<TextView>(R.id.title).setText(title)
        val iconView = row.findViewById<ImageView>(R.id.icon)
        iconView.setImageResource(icon)
        row.setOnFocusChangeListener { v, hasFocus ->
            iconView.setColorFilter(
                if (hasFocus) Color.parseColor("#FF07080A") else Color.WHITE
            )
            v.animate().setDuration(Motion.FOCUS_IN_MS)
                .setInterpolator(Motion.standard).start()
        }
        iconView.setColorFilter(Color.WHITE)
        row.setOnClickListener { action() }
        container.addView(row)
    }

    override fun onStart() {
        super.onStart()
        findViewById<View>(R.id.menuItems)?.alpha = 0f
        findViewById<View>(R.id.menuItems)?.animate()?.alpha(1f)
            ?.setDuration(Motion.FOCUS_IN_MS)?.start()
    }
}
