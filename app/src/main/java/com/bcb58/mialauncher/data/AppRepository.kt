package com.bcb58.mialauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.annotation.WorkerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * The single source of truth for the app list.
 *
 * Start-up strategy: the previous list is mirrored into one tab-separated file.
 * Reading it costs ~1 ms, so the home screen can be drawn on the very first
 * frame, while [refresh] re-queries PackageManager (~150-400 ms on a Mi Box)
 * off the main thread and only re-publishes when something actually changed.
 */
object AppRepository {

    private const val CACHE_FILE = "applist.tsv"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshLock = Mutex()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    @Volatile
    private var warmed = false

    /** Call once from Application.onCreate(). Never blocks the caller for long. */
    fun warmUp(context: Context) {
        if (warmed) return
        warmed = true
        val app = context.applicationContext
        scope.launch {
            readCache(app)?.let { if (_apps.value.isEmpty()) _apps.value = it }
            refresh(app)
        }
    }

    /** Synchronous, main-thread-safe best effort used by the first frame. */
    fun snapshot(context: Context): List<AppEntry> {
        _apps.value.takeIf { it.isNotEmpty() }?.let { return it }
        return readCache(context.applicationContext).orEmpty()
    }

    fun requestRefresh(context: Context) {
        val app = context.applicationContext
        scope.launch { refresh(app) }
    }

    @WorkerThread
    private suspend fun refresh(context: Context) = refreshLock.withLock {
        val fresh = query(context)
        if (fresh.isEmpty()) return@withLock
        if (fresh != _apps.value) {
            _apps.value = fresh
            writeCache(context, fresh)
        }
    }

    @WorkerThread
    private fun query(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val self = context.packageName
        // Map key is the package: a TV activity always wins over a phone one, so
        // apps that ship both entry points show up exactly once.
        val best = HashMap<String, Pair<ResolveInfo, Boolean>>(96)

        fun collect(category: String, tv: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val resolved = try {
                pm.queryIntentActivities(intent, 0)
            } catch (t: Throwable) {
                emptyList<ResolveInfo>()
            }
            for (ri in resolved) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == self) continue
                if (!ai.exported || !ai.enabled) continue
                val existing = best[ai.packageName]
                if (existing == null || (tv && !existing.second)) {
                    best[ai.packageName] = ri to tv
                }
            }
        }

        // Phone entry points first so the TV pass can override them.
        collect(Intent.CATEGORY_LAUNCHER, tv = false)
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, tv = true)

        val out = ArrayList<AppEntry>(best.size)
        for ((_, pair) in best) {
            val (ri, tv) = pair
            val ai = ri.activityInfo
            val label = try {
                ri.loadLabel(pm)?.toString()?.trim().orEmpty()
            } catch (t: Throwable) {
                ""
            }.ifEmpty { ai.packageName }

            out += AppEntry(
                packageName = ai.packageName,
                activityName = ai.name,
                label = label,
                isTvApp = tv,
                hasBanner = ai.banner != 0 || (ai.applicationInfo?.banner ?: 0) != 0,
                isSystem = (ai.applicationInfo?.flags ?: 0) and
                    (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                versionCode = versionCodeOf(pm, ai.packageName)
            )
        }
        out.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        return out
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(pm: PackageManager, pkg: String): Long = try {
        val info = pm.getPackageInfo(pkg, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else info.versionCode.toLong()
    } catch (t: Throwable) {
        0L
    }

    // ---------------------------------------------------------------- cache

    private fun cacheFile(context: Context) = File(context.filesDir, CACHE_FILE)

    private fun readCache(context: Context): List<AppEntry>? {
        val f = cacheFile(context)
        if (!f.exists()) return null
        return try {
            val out = ArrayList<AppEntry>(96)
            f.bufferedReader().useLines { lines ->
                for (l in lines) AppEntry.fromLine(l)?.let(out::add)
            }
            out.takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            null
        }
    }

    private fun writeCache(context: Context, apps: List<AppEntry>) {
        try {
            val tmp = File(context.filesDir, "$CACHE_FILE.tmp")
            tmp.bufferedWriter().use { w ->
                for (a in apps) {
                    w.write(a.toLine())
                    w.write("\n")
                }
            }
            if (!tmp.renameTo(cacheFile(context))) tmp.delete()
        } catch (t: Throwable) {
            // A stale cache is harmless: the next refresh rebuilds it.
        }
    }

    // ----------------------------------------------------------------- rows

    /**
     * Builds the home screen rows. Pure function of [apps] + [prefs], so it is
     * cheap to re-run on every change instead of mutating adapters in place.
     */
    fun buildRows(
        apps: List<AppEntry>,
        prefs: Prefs,
        favoritesTitle: String,
        recentsTitle: String,
        tvTitle: String,
        otherTitle: String
    ): List<AppRow> {
        val hidden = prefs.hiddenApps
        val visible = apps.filterNot { hidden.contains(it.id) }
        if (visible.isEmpty()) return emptyList()

        val byId = visible.associateBy { it.id }
        val rows = ArrayList<AppRow>(4)

        val favorites = prefs.favorites.mapNotNull(byId::get)
        val favoriteIds = favorites.mapTo(HashSet()) { it.id }
        if (favorites.isNotEmpty()) {
            rows += AppRow("fav", favoritesTitle, favorites)
        }

        if (prefs.showRecents) {
            val recents = visible
                .filter { prefs.lastLaunch(it.id) > 0L && !favoriteIds.contains(it.id) }
                .sortedByDescending { prefs.lastLaunch(it.id) }
                .take(12)
            if (recents.isNotEmpty()) rows += AppRow("recent", recentsTitle, recents)
        }

        val rest = visible.filterNot { favoriteIds.contains(it.id) }
        val tv = rest.filter { it.isTvApp }
        val other = rest.filterNot { it.isTvApp }
        if (tv.isNotEmpty()) rows += AppRow("tv", tvTitle, tv)
        if (other.isNotEmpty()) rows += AppRow("other", otherTitle, other, AppRow.Style.TILE)

        return rows
    }
}
