package com.bcb58.mialauncher.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.bcb58.mialauncher.R

object Wallpapers {

    @DrawableRes
    fun drawableFor(index: Int): Int = when (index) {
        1 -> R.drawable.wall_1
        2 -> R.drawable.wall_2
        3 -> R.drawable.wall_3
        4 -> R.drawable.wall_4
        else -> R.drawable.wall_0
    }

    @StringRes
    fun nameFor(index: Int): Int = when (index) {
        1 -> R.string.wallpaper_graphite
        2 -> R.string.wallpaper_midnight
        3 -> R.string.wallpaper_plum
        4 -> R.string.wallpaper_forest
        else -> R.string.wallpaper_black
    }

    const val COUNT = 5
}
