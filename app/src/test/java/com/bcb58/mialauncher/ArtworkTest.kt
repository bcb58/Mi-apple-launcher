package com.bcb58.mialauncher

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.bcb58.mialauncher.data.AppEntry
import com.bcb58.mialauncher.data.Artwork
import com.bcb58.mialauncher.util.Colors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun register(pkg: String, iconRes: Int): AppEntry {
        val ctx = context()
        val component = ComponentName(pkg, "$pkg.MainActivity")
        shadowOf(ctx.packageManager).addOrUpdateActivity(ActivityInfo().apply {
            packageName = pkg
            name = component.className
            nonLocalizedLabel = pkg
            applicationInfo = ctx.applicationInfo
            icon = iconRes
        })
        return AppEntry(pkg, component.className, pkg, true, false, false, 1L)
    }

    @Test
    fun `cards have genuinely transparent rounded corners`() {
        val entry = register("com.example.rounded", R.mipmap.ic_launcher)
        val (bitmap, _) = Artwork.loadBlocking(context(), entry, 216, 122)!!

        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(0, Color.alpha(bitmap.getPixel(215, 0)))
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 121)))
        assertEquals(0, Color.alpha(bitmap.getPixel(215, 121)))
        // The middle of each edge, and the centre, must stay fully opaque.
        assertEquals(255, Color.alpha(bitmap.getPixel(108, 0)))
        assertEquals(255, Color.alpha(bitmap.getPixel(0, 61)))
        assertEquals(255, Color.alpha(bitmap.getPixel(108, 61)))
    }

    @Test
    fun `the disk cache preserves corner transparency across a cold start`() {
        val entry = register("com.example.disk", R.mipmap.ic_launcher)
        assertNotNull(Artwork.loadBlocking(context(), entry, 216, 122))

        // Drop the in-memory tier so the next read must come off disk.
        Artwork.invalidateMemory()
        assertNull("memory tier should be empty",
            Artwork.cached(Artwork.keyFor(entry, 216, 122)))

        val (fromDisk, _) = Artwork.loadBlocking(context(), entry, 216, 122)!!
        assertEquals(0, Color.alpha(fromDisk.getPixel(0, 0)))
        assertEquals(255, Color.alpha(fromDisk.getPixel(108, 61)))
    }

    @Test
    fun `cache keys separate apps, sizes and versions`() {
        val a = AppEntry("com.a", "com.a.Main", "A", true, false, false, 1L)
        val keys = setOf(
            Artwork.keyFor(a, 216, 122),
            Artwork.keyFor(a, 150, 85),
            Artwork.keyFor(a.copy(versionCode = 2L), 216, 122),
            Artwork.keyFor(a.copy(activityName = "com.a.Other"), 216, 122)
        )
        assertEquals("every variant needs its own key", 4, keys.size)
    }

    @Test
    fun `a greyscale icon yields graphite rather than an invented hue`() {
        val grey = android.graphics.Bitmap.createBitmap(16, 16,
            android.graphics.Bitmap.Config.ARGB_8888)
        grey.eraseColor(Color.WHITE)
        assertEquals(Colors.NEUTRAL, Colors.accentOf(grey))
    }

    @Test
    fun `a colourful icon keeps its hue inside the TV-safe band`() {
        val orange = android.graphics.Bitmap.createBitmap(16, 16,
            android.graphics.Bitmap.Config.ARGB_8888)
        orange.eraseColor(Color.rgb(255, 120, 0))
        val accent = Colors.accentOf(orange)

        val hsv = FloatArray(3)
        Color.colorToHSV(accent, hsv)
        // The band is checked with a small tolerance: clamping happens in HSV
        // but the result round-trips through 8-bit RGB.
        val tolerance = 0.01f
        assertTrue("hue should be preserved, was ${hsv[0]}", hsv[0] in 15f..40f)
        assertTrue("saturation out of band: ${hsv[1]}",
            hsv[1] in (0.26f - tolerance)..(0.72f + tolerance))
        assertTrue("value out of band: ${hsv[2]}",
            hsv[2] in (0.34f - tolerance)..(0.62f + tolerance))
    }

    @Test
    fun `an unknown package degrades to a neutral card instead of throwing`() {
        val ghost = AppEntry("com.not.installed", "com.not.installed.Main",
            "Ghost", true, false, false, 1L)
        val result = Artwork.loadBlocking(context(), ghost, 216, 122)
        assertNotNull("missing apps must still get a card", result)
        assertEquals(216, result!!.first.width)
    }
}
