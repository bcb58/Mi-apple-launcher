package com.bcb58.mialauncher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.bcb58.mialauncher.R

/**
 * An Apple-TV-style focusable card.
 *
 * On focus it does four things at once, which together are what the eye reads
 * as "tvOS": a spring-driven scale with a little overshoot, a shadow that
 * blooms as the card lifts, a specular highlight that sweeps diagonally across
 * the artwork, and a short 3D tilt away from the direction focus arrived from.
 *
 * Everything is driven by a single [ValueAnimator] plus two springs, and the
 * view promotes itself to a hardware layer only while animating, which keeps
 * a full row of these smooth at 60 fps on the Mi Box's Mali-G31.
 */
class FocusCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private var cornerRadius = 12f * density

    private val clipPath = Path()
    private val bounds = RectF()

    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
    }

    /** 0 = unfocused, 1 = fully focused. Drives rim, shadow and sheen alpha. */
    private var focusAmount = 0f
        set(value) {
            if (field != value) {
                field = value
                applyLift()
                invalidate()
            }
        }

    /** -0.35..1 — position of the highlight sweep; below 0 it is off-screen. */
    private var sheenProgress = -0.35f
        set(value) {
            field = value
            invalidate()
        }

    private var focusAnimator: ValueAnimator? = null
    private var sheenAnimator: ValueAnimator? = null

    private val scaleSpringX = spring(DynamicAnimation.SCALE_X)
    private val scaleSpringY = spring(DynamicAnimation.SCALE_Y)

    private val restingElevation = 0f
    private val focusedElevation = 18f * density

    var tiltEnabled: Boolean = true

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        clipChildren = true
        clipToOutline = true
        setWillNotDraw(false)
        cameraDistance = 9000f * density

        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }

        val ta = context.theme.obtainStyledAttributes(
            attrs, R.styleable.FocusCardView, defStyleAttr, 0
        )
        try {
            cornerRadius = ta.getDimension(
                R.styleable.FocusCardView_cardCornerRadius, cornerRadius
            )
        } finally {
            ta.recycle()
        }
    }

    private fun spring(property: FloatPropertyCompat<View>) =
        SpringAnimation(this, property).apply {
            this.spring = SpringForce().apply {
                stiffness = Motion.SPRING_STIFFNESS
                dampingRatio = Motion.SPRING_DAMPING
            }
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
        invalidateOutline()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animateFocus(gainFocus)
    }

    private fun animateFocus(focused: Boolean) {
        focusAnimator?.cancel()

        val target = if (focused) 1f else 0f
        focusAnimator = ValueAnimator.ofFloat(focusAmount, target).apply {
            duration = if (focused) Motion.FOCUS_IN_MS else Motion.FOCUS_OUT_MS
            interpolator = if (focused) Motion.decelerate else Motion.standard
            addUpdateListener { focusAmount = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!focused) setLayerType(LAYER_TYPE_NONE, null)
                }
            })
            start()
        }

        val scale = if (focused) Motion.FOCUSED_SCALE else 1f
        scaleSpringX.animateToFinalPosition(scale)
        scaleSpringY.animateToFinalPosition(scale)

        if (focused) {
            setLayerType(LAYER_TYPE_HARDWARE, null)
            // Never bringToFront(): in the card's vertical LinearLayout that
            // reorders children and drops the label above the artwork. Depth
            // comes from translationZ plus the row's child drawing order.
            (parent as? View)?.invalidate()
            startSheen()
            if (tiltEnabled) startTilt()
        } else {
            sheenAnimator?.cancel()
            sheenProgress = -0.35f
            animate().cancel()
            rotationX = 0f
            rotationY = 0f
        }
    }

    private fun applyLift() {
        elevation = restingElevation + (focusedElevation - restingElevation) * focusAmount
        translationZ = focusAmount * 2f * density
    }

    private fun startSheen() {
        sheenAnimator?.cancel()
        sheenAnimator = ValueAnimator.ofFloat(-0.35f, 1.35f).apply {
            duration = Motion.SHEEN_MS
            interpolator = Motion.emphasised
            addUpdateListener { sheenProgress = it.animatedValue as Float }
            start()
        }
    }

    /**
     * A single settle: snap to a tilted pose matching the travel direction and
     * spring back to flat. Cheap, and it sells the depth of the card.
     */
    private fun startTilt() {
        when (FocusDirection.last) {
            FocusDirection.RIGHT -> { rotationY = Motion.TILT_DEGREES; rotationX = 0f }
            FocusDirection.LEFT -> { rotationY = -Motion.TILT_DEGREES; rotationX = 0f }
            FocusDirection.DOWN -> { rotationX = -Motion.TILT_DEGREES; rotationY = 0f }
            FocusDirection.UP -> { rotationX = Motion.TILT_DEGREES; rotationY = 0f }
            else -> { rotationX = 0f; rotationY = 0f }
        }
        animate()
            .rotationX(0f)
            .rotationY(0f)
            .setDuration(360L)
            .setInterpolator(Motion.decelerate)
            .start()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (focusAmount <= 0.01f || width == 0) return

        val save = canvas.save()
        canvas.clipPath(clipPath)

        // Diagonal specular sweep.
        if (sheenProgress > -0.3f && sheenProgress < 1.3f) {
            val w = width.toFloat()
            val h = height.toFloat()
            val bandWidth = w * 0.42f
            val x = -bandWidth + sheenProgress * (w + bandWidth * 2f)
            sheenPaint.shader = LinearGradient(
                x, 0f, x + bandWidth, h,
                intArrayOf(Color.TRANSPARENT, Color.argb(72, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            sheenPaint.alpha = (focusAmount * 255f).toInt()
            canvas.drawRect(0f, 0f, w, h, sheenPaint)
        }

        // Light rim: keeps the selection legible from across the room.
        rimPaint.alpha = (focusAmount * 150f).toInt()
        val inset = rimPaint.strokeWidth / 2f
        canvas.drawRoundRect(
            inset, inset, width - inset, height - inset,
            cornerRadius, cornerRadius, rimPaint
        )

        canvas.restoreToCount(save)
    }

    /** Reset state when a holder is recycled, otherwise focus visuals leak. */
    fun resetVisuals() {
        focusAnimator?.cancel()
        sheenAnimator?.cancel()
        animate().cancel()
        scaleSpringX.cancel()
        scaleSpringY.cancel()
        focusAmount = 0f
        sheenProgress = -0.35f
        scaleX = 1f
        scaleY = 1f
        rotationX = 0f
        rotationY = 0f
        elevation = restingElevation
        translationZ = 0f
        setLayerType(LAYER_TYPE_NONE, null)
    }
}
