package com.bcb58.mialauncher.util

import android.graphics.Bitmap

/**
 * Three-pass box blur, which converges on a Gaussian and costs O(n) per pass.
 * Always run on a heavily downscaled bitmap (we use ~96 px wide), so the whole
 * thing is well under a millisecond even on an Amlogic A55 core — no
 * RenderScript, no RenderEffect, works back to API 21.
 */
object Blur {

    fun apply(src: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return src
        val w = src.width
        val h = src.height
        if (w < 3 || h < 3) return src

        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)

        val r = radius.coerceAtMost(minOf(w, h) / 2).coerceAtLeast(1)
        repeat(3) {
            boxBlurHorizontal(pix, tmp, w, h, r)
            boxBlurVertical(tmp, pix, w, h, r)
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pix, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val span = r + r + 1
        for (y in 0 until h) {
            val row = y * w
            var a = 0; var rr = 0; var gg = 0; var bb = 0
            for (i in -r..r) {
                val c = src[row + i.coerceIn(0, w - 1)]
                a += c ushr 24 and 0xFF; rr += c ushr 16 and 0xFF
                gg += c ushr 8 and 0xFF; bb += c and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = (a / span shl 24) or (rr / span shl 16) or
                    (gg / span shl 8) or (bb / span)
                val out = src[row + (x - r).coerceIn(0, w - 1)]
                val inn = src[row + (x + r + 1).coerceIn(0, w - 1)]
                a += (inn ushr 24 and 0xFF) - (out ushr 24 and 0xFF)
                rr += (inn ushr 16 and 0xFF) - (out ushr 16 and 0xFF)
                gg += (inn ushr 8 and 0xFF) - (out ushr 8 and 0xFF)
                bb += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val span = r + r + 1
        for (x in 0 until w) {
            var a = 0; var rr = 0; var gg = 0; var bb = 0
            for (i in -r..r) {
                val c = src[i.coerceIn(0, h - 1) * w + x]
                a += c ushr 24 and 0xFF; rr += c ushr 16 and 0xFF
                gg += c ushr 8 and 0xFF; bb += c and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = (a / span shl 24) or (rr / span shl 16) or
                    (gg / span shl 8) or (bb / span)
                val out = src[(y - r).coerceIn(0, h - 1) * w + x]
                val inn = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                a += (inn ushr 24 and 0xFF) - (out ushr 24 and 0xFF)
                rr += (inn ushr 16 and 0xFF) - (out ushr 16 and 0xFF)
                gg += (inn ushr 8 and 0xFF) - (out ushr 8 and 0xFF)
                bb += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}
