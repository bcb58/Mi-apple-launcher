package com.bcb58.mialauncher

import android.graphics.Bitmap
import android.graphics.Color
import com.bcb58.mialauncher.util.Blur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlurTest {

    @Test
    fun `blur keeps dimensions and softens a hard edge`() {
        val src = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888)
        for (y in 0 until 54) {
            for (x in 0 until 96) {
                src.setPixel(x, y, if (x < 48) Color.BLACK else Color.WHITE)
            }
        }
        val out = Blur.apply(src, 10)
        assertEquals(96, out.width)
        assertEquals(54, out.height)

        // The pixel right at the former hard edge must now be mid-grey.
        val mid = Color.red(out.getPixel(48, 27))
        assertTrue("edge pixel should be softened, was $mid", mid in 60..200)
    }

    @Test
    fun `degenerate inputs are returned untouched instead of crashing`() {
        val tiny = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        assertSame(tiny, Blur.apply(tiny, 8))
        val normal = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        assertSame(normal, Blur.apply(normal, 0))
    }

    @Test
    fun `a radius larger than the bitmap is clamped`() {
        val src = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.RED)
        val out = Blur.apply(src, 500)
        assertEquals(16, out.width)
        assertEquals(Color.RED, out.getPixel(8, 8))
    }
}
