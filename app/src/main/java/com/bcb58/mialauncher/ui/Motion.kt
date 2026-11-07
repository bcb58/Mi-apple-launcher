package com.bcb58.mialauncher.ui

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * One place for timing, so every surface moves with the same personality.
 * Values are matched to tvOS: fast attack, soft settle, slight overshoot.
 */
object Motion {

    /** Apple's standard ease-in-out, used for everything that is not a spring. */
    val standard: Interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    /** For the thing the user just pointed at: nearly instant, then glides. */
    val decelerate: Interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    val emphasised: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    const val FOCUS_IN_MS = 220L
    const val FOCUS_OUT_MS = 260L
    const val SHEEN_MS = 520L
    const val BACKDROP_MS = 420L
    const val ROW_SCROLL_MS = 320L

    const val FOCUSED_SCALE = 1.11f
    const val TILT_DEGREES = 5.5f

    /** Spring constants; dampingRatio below 1 is what produces the pop. */
    const val SPRING_STIFFNESS = 520f
    const val SPRING_DAMPING = 0.64f
}

/**
 * The direction the user last travelled, so a card can tilt away from where
 * focus came from instead of always tilting the same way.
 */
object FocusDirection {
    const val NONE = 0
    const val LEFT = 1
    const val RIGHT = 2
    const val UP = 3
    const val DOWN = 4

    @Volatile
    var last: Int = NONE
}
