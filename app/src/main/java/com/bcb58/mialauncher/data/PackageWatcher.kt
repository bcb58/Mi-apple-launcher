package com.bcb58.mialauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Keeps the list honest when apps are installed, updated or removed. */
class PackageWatcher : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Only react to the package broadcasts we registered for, and never to
        // our own package being updated (that restarts us anyway).
        if (intent.action !in HANDLED) return
        if (intent.data?.schemeSpecificPart == context.packageName) return

        // PACKAGE_REPLACED arrives twice (remove + add); a debounced refresh in
        // the repository makes the duplicate free.
        intent.data?.schemeSpecificPart?.let { Artwork.invalidate(context, it) }
        AppRepository.requestRefresh(context)
        context.sendBroadcast(
            Intent(ACTION_APPS_CHANGED).setPackage(context.packageName)
        )
    }

    companion object {
        private val HANDLED = setOf(
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_CHANGED
        )

        const val ACTION_APPS_CHANGED = "com.bcb58.mialauncher.APPS_CHANGED"
    }
}
