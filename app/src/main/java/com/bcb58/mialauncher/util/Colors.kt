package com.bcb58.mialauncher.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette
import kotlin.math.max
import kotlin.math.min

object Colors {

    /** Picks a saturated, TV-safe accent from an icon. */
    fun accentOf(bitmap: Bitmap): Int {
        val small = if (bitmap.width > 64) {
            Bitmap.createScaledBitmap(bitmap, 48, 48, true)
        } else {
            bitmap
        }
        val palette = try {
            Palette.from(small).clearFilters().maximumColorCount(12).generate()
        } catch (t: Throwable) {
            null
        }
        if (small !== bitmap) small.recycle()

        val raw = palette?.let {
            it.vibrantSwatch?.rgb
                ?: it.lightVibrantSwatch?.rgb
                ?: it.darkVibrantSwatch?.rgb
                ?: it.dominantSwatch?.rgb
                ?: it.mutedSwatch?.rgb
        } ?: 0xFF3A3A3C.toInt()

        return normalize(raw)
    }

    /**
     * Keeps card backgrounds in a narrow band: enough colour to identify the
     * app, never so bright that the focus highlight stops reading.
     */
    private fun normalize(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        // A white or grey icon has no hue worth amplifying: forcing saturation
        // on it just tints every such card the same muddy brown.
        if (hsv[1] < 0.12f) return NEUTRAL
        hsv[1] = min(max(hsv[1], 0.26f), 0.72f)
        hsv[2] = min(max(hsv[2], 0.34f), 0.62f)
        return Color.HSVToColor(hsv)
    }

    /** Graphite, used for icons with no usable colour of their own. */
    const val NEUTRAL = 0xFF34373F.toInt()

    fun shade(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha * 255f).toInt().coerceIn(0, 255),
            Color.red(color), Color.green(color), Color.blue(color))
}
