package com.bcb58.mialauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.annotation.WorkerThread
import androidx.core.graphics.drawable.toBitmap
import com.bcb58.mialauncher.util.Blur
import com.bcb58.mialauncher.util.Colors
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Produces and caches the 16:9 cards the home screen is made of.
 *
 * Three tiers, in order: an in-memory [LruCache], a lossy WebP disk cache in
 * `cacheDir/art`, and only then the expensive path (ask PackageManager for the
 * drawable, extract a palette, composite a gradient card). Once a device has
 * been through the home screen one time, scrolling never touches tier three.
 */
object Artwork {

    /** Exactly two decoders: leaves two of the Mi Box's four cores to the UI. */
    private val io = Executors.newFixedThreadPool(2, ThreadFactory { r ->
        Thread(r, "mial-art").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 2
        }
    })

    private val memory = object : LruCache<String, Bitmap>(memoryBudget()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val accents = LruCache<String, Int>(256)

    private val backdrops = LruCache<String, Bitmap>(12)

    private fun memoryBudget(): Int {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        // ~1/6 of the heap, clamped so a 2 GB box with a 192 MB heap stays calm.
        return maxKb.div(6).coerceIn(4 * 1024, 24 * 1024)
    }

    fun cached(key: String): Bitmap? = memory.get(key)

    fun accent(key: String): Int? = accents.get(key)

    fun keyFor(entry: AppEntry, w: Int, h: Int): String =
        "${entry.packageName}|${entry.activityName}|${entry.versionCode}|${w}x$h"

    /**
     * Resolves a card asynchronously. [onReady] runs on the calling thread's
     * looper only when the request is still the newest one for [token].
     */
    fun load(
        context: Context,
        entry: AppEntry,
        w: Int,
        h: Int,
        onReady: (Bitmap, Int) -> Unit,
        onCancelled: () -> Boolean
    ) {
        val key = keyFor(entry, w, h)
        memory.get(key)?.let { bmp ->
            onReady(bmp, accents.get(key) ?: DEFAULT_ACCENT)
            return
        }
        val app = context.applicationContext
        io.execute {
            if (onCancelled()) return@execute
            val result = runCatching { build(app, entry, w, h) }.getOrNull() ?: return@execute
            memory.put(key, result.first)
            accents.put(key, result.second)
            if (!onCancelled()) onReady(result.first, result.second)
        }
    }

    @WorkerThread
    fun loadBlocking(context: Context, entry: AppEntry, w: Int, h: Int): Pair<Bitmap, Int>? {
        val key = keyFor(entry, w, h)
        memory.get(key)?.let { return it to (accents.get(key) ?: DEFAULT_ACCENT) }
        val built = runCatching { build(context.applicationContext, entry, w, h) }.getOrNull()
            ?: return null
        memory.put(key, built.first)
        accents.put(key, built.second)
        return built
    }

    // ------------------------------------------------------------- building

    @WorkerThread
    private fun build(context: Context, entry: AppEntry, w: Int, h: Int): Pair<Bitmap, Int>? {
        val key = keyFor(entry, w, h)
        readDisk(context, key)?.let { disk ->
            val accent = accents.get(key) ?: Colors.accentOf(disk)
            return disk to accent
        }

        val pm = context.packageManager
        val banner: Drawable? = if (entry.hasBanner) {
            runCatching { pm.getActivityBanner(componentOf(entry)) }.getOrNull()
                ?: runCatching { pm.getApplicationBanner(entry.packageName) }.getOrNull()
        } else {
            null
        }

        val icon: Drawable? = runCatching { pm.getActivityIcon(componentOf(entry)) }.getOrNull()
            ?: runCatching { pm.getApplicationIcon(entry.packageName) }.getOrNull()

        val iconBitmap = icon?.let { safeBitmap(it, 128) }
        val accent = iconBitmap?.let { Colors.accentOf(it) } ?: DEFAULT_ACCENT

        val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)

        if (banner != null) {
            drawCentreCrop(canvas, safeBitmap(banner, max(w, 640)), w, h)
        } else {
            paintGradientCard(canvas, w, h, accent)
            if (iconBitmap != null) drawIconCentred(canvas, iconBitmap, w, h)
        }

        // Bake the rounded corners into the bitmap. Relying on clipToOutline
        // alone leaves square corners wherever outline clipping is unsupported,
        // and this costs nothing at scroll time.
        val out = roundCorners(flat, cornerRadius(context))
        if (out !== flat) flat.recycle()

        writeDisk(context, key, out)
        return out to accent
    }

    private fun cornerRadius(context: Context): Float =
        context.resources.getDimension(com.bcb58.mialauncher.R.dimen.card_radius)

    private fun roundCorners(src: Bitmap, radius: Float): Bitmap {
        if (radius <= 0f) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        Canvas(out).drawRoundRect(
            RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()),
            radius, radius, paint
        )
        return out
    }

    private fun componentOf(entry: AppEntry) =
        android.content.ComponentName(entry.packageName, entry.activityName)

    private fun safeBitmap(drawable: Drawable, maxEdge: Int): Bitmap {
        val iw = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxEdge
        val ih = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxEdge
        val scale = min(1f, maxEdge.toFloat() / max(iw, ih))
        val w = (iw * scale).roundToInt().coerceAtLeast(1)
        val h = (ih * scale).roundToInt().coerceAtLeast(1)
        return drawable.toBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

    private fun drawCentreCrop(canvas: Canvas, src: Bitmap, w: Int, h: Int) {
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw = (w / scale).roundToInt().coerceAtMost(src.width)
        val sh = (h / scale).roundToInt().coerceAtMost(src.height)
        val sx = (src.width - sw) / 2
        val sy = (src.height - sh) / 2
        canvas.drawBitmap(
            src,
            Rect(sx, sy, sx + sw, sy + sh),
            Rect(0, 0, w, h),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
    }

    /**
     * The fallback card: a top-lit diagonal gradient, which is what makes a
     * phone app with only a square icon still look like it belongs next to a
     * real TV banner.
     */
    private fun paintGradientCard(canvas: Canvas, w: Int, h: Int, accent: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            Colors.shade(accent, 1.18f),
            Colors.shade(accent, 0.55f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // Specular sheen baked top-left, mirroring tvOS' lit icon look.
        val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
        gloss.shader = RadialGradient(
            w * 0.22f, -h * 0.25f, h * 1.25f,
            intArrayOf(Color.argb(74, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gloss)

        val floorShade = Paint(Paint.ANTI_ALIAS_FLAG)
        floorShade.shader = LinearGradient(
            0f, h * 0.55f, 0f, h.toFloat(),
            Color.TRANSPARENT, Color.argb(66, 0, 0, 0),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * 0.55f, w.toFloat(), h.toFloat(), floorShade)
    }

    private fun drawIconCentred(canvas: Canvas, icon: Bitmap, w: Int, h: Int) {
        val target = (h * 0.56f).roundToInt()
        val scale = target.toFloat() / max(icon.width, icon.height)
        val dw = (icon.width * scale).roundToInt()
        val dh = (icon.height * scale).roundToInt()
        val left = (w - dw) / 2
        val top = (h - dh) / 2
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        // A soft drop shadow lifts the glyph off the gradient.
        paint.setShadowLayer(h * 0.05f, 0f, h * 0.02f, Color.argb(110, 0, 0, 0))
        canvas.drawBitmap(icon, null, Rect(left, top, left + dw, top + dh), paint)
    }

    // ----------------------------------------------------------- disk cache

    private fun artDir(context: Context): File =
        File(context.cacheDir, "art").apply { if (!exists()) mkdirs() }

    private fun diskFile(context: Context, key: String) =
        File(artDir(context), "${key.hashCode().toUInt().toString(16)}.png")

    private fun readDisk(context: Context, key: String): Bitmap? {
        val f = diskFile(context, key)
        if (!f.exists()) return null
        return runCatching {
            android.graphics.BitmapFactory.decodeFile(f.absolutePath)
        }.getOrNull()
    }

    private fun writeDisk(context: Context, key: String, bitmap: Bitmap) {
        runCatching {
            diskFile(context, key).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    /** Drops artwork for a package after an update or uninstall. */
    fun invalidate(context: Context, packageName: String) {
        val keys = memory.snapshot().keys.filter { it.startsWith("$packageName|") }
        for (k in keys) {
            memory.remove(k)
            accents.remove(k)
            runCatching { diskFile(context, k).delete() }
        }
    }

    fun trimMemory() {
        memory.trimToSize(memory.size() / 2)
    }

    /** Drops the in-memory tier only; the disk cache survives. */
    @androidx.annotation.VisibleForTesting
    fun invalidateMemory() {
        memory.evictAll()
        accents.evictAll()
        backdrops.evictAll()
    }

    // -------------------------------------------------------------- backdrop

    /**
     * The ambient background: the focused card, downscaled hard and blurred.
     * At 96x54 px the blur is free and the upscale is done by the GPU.
     */
    fun backdrop(key: String, source: Bitmap): Bitmap {
        backdrops.get(key)?.let { return it }
        val small = Bitmap.createScaledBitmap(source, 96, 54, true)
        val blurred = Blur.apply(small, 12)
        if (blurred !== small) small.recycle()
        backdrops.put(key, blurred)
        return blurred
    }

    const val DEFAULT_ACCENT = 0xFF3A3A3C.toInt()
}
