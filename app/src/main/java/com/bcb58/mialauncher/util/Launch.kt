package com.bcb58.mialauncher.util

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.Toast
import com.bcb58.mialauncher.R
import com.bcb58.mialauncher.data.AppEntry
import com.bcb58.mialauncher.data.Prefs

object Launch {

    /**
     * Starts an app from its exact component, which skips the resolver and is
     * noticeably quicker than going through getLaunchIntentForPackage.
     */
    fun app(activity: Activity, entry: AppEntry, from: View?): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(entry.packageName, entry.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        val options = from?.let {
            ActivityOptions.makeScaleUpAnimation(it, 0, 0, it.width, it.height).toBundle()
        }
        return try {
            activity.startActivity(intent, options)
            Prefs.get(activity).noteLaunch(entry.id)
            true
        } catch (t: Throwable) {
            // Some packages only expose a LEANBACK entry point under a
            // different category, so fall back to the resolved launch intent.
            val fallback = activity.packageManager
                .getLeanbackLaunchIntentForPackageCompat(entry.packageName)
                ?: activity.packageManager.getLaunchIntentForPackage(entry.packageName)
            if (fallback != null) {
                fallback.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    activity.startActivity(fallback)
                    Prefs.get(activity).noteLaunch(entry.id)
                    return true
                } catch (ignored: Throwable) {
                }
            }
            toast(activity, R.string.toast_cannot_open)
            false
        }
    }

    private fun android.content.pm.PackageManager.getLeanbackLaunchIntentForPackageCompat(
        pkg: String
    ): Intent? = try {
        getLeanbackLaunchIntentForPackage(pkg)
    } catch (t: Throwable) {
        null
    }

    fun appInfo(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startOrToast(context, intent, R.string.toast_no_settings)
    }

    @Suppress("DEPRECATION")
    fun uninstall(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE)
            .setData(Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startOrToast(context, intent, R.string.toast_no_settings)
    }

    /** Opens the TV's own settings; falls back to the phone settings screen. */
    fun systemSettings(context: Context) {
        val candidates = listOf(
            Intent("android.settings.SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (tryStart(context, intent)) return
        }
        toast(context, R.string.toast_no_settings)
    }

    /**
     * Opens the "home app" chooser. ACTION_HOME_SETTINGS exists on Android 10+;
     * older TVs get the generic settings screen instead.
     */
    fun defaultLauncherSettings(context: Context) {
        val intents = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intents += Intent(Settings.ACTION_HOME_SETTINGS)
        }
        intents += Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        intents += Intent(Settings.ACTION_SETTINGS)
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (tryStart(context, intent)) return
        }
        toast(context, R.string.toast_no_settings)
    }

    private fun startOrToast(context: Context, intent: Intent, messageRes: Int) {
        if (!tryStart(context, intent)) toast(context, messageRes)
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (t: Throwable) {
        false
    }

    fun toast(context: Context, messageRes: Int) {
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
    }

    fun toast(context: Context, message: CharSequence) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
