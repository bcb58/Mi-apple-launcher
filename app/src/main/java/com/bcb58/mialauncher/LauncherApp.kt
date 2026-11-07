package com.bcb58.mialauncher

import android.app.Application
import android.content.ComponentCallbacks2
import com.bcb58.mialauncher.data.AppRepository
import com.bcb58.mialauncher.data.Artwork

class LauncherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Kick the package query off immediately: by the time the first frame
        // is composed the cached list is already published.
        AppRepository.warmUp(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            Artwork.trimMemory()
        }
    }
}
