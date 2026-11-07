package com.bcb58.mialauncher.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * A horizontal strip that behaves like a tvOS shelf: the selection stays put
 * near the left safe-area edge and the content glides underneath it, instead
 * of RecyclerView's default "scroll by the smallest amount that works", which
 * makes a D-pad feel like it is dragging a scrollbar.
 */
class TvRowRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        layoutManager = object : LinearLayoutManager(context, HORIZONTAL, false) {
            // Keep a couple of cards warm off-screen so a fast press never
            // lands on an empty placeholder.
            override fun getExtraLayoutSpace(state: State) = width / 2
        }
        setHasFixedSize(true)
        itemAnimator = null
        isFocusable = false
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        clipChildren = false
        clipToPadding = false
        overScrollMode = OVER_SCROLL_NEVER
        setItemViewCacheSize(6)
        isChildrenDrawingOrderEnabled = true
    }

    override fun requestChildRectangleOnScreen(
        child: View,
        rect: Rect,
        immediate: Boolean
    ): Boolean {
        val dx = child.left - paddingLeft
        if (dx == 0) return false
        if (immediate) {
            scrollBy(dx, 0)
        } else {
            smoothScrollBy(dx, 0, Motion.decelerate, Motion.ROW_SCROLL_MS.toInt())
        }
        return true
    }

    /** Draws the focused card last so its shadow is never cut by a neighbour. */
    override fun getChildDrawingOrder(childCount: Int, i: Int): Int {
        val focused = focusedChild ?: return i
        val focusedIndex = indexOfChild(focused)
        if (focusedIndex < 0) return i
        return when {
            i == childCount - 1 -> focusedIndex
            i >= focusedIndex -> i + 1
            else -> i
        }
    }
}

/**
 * The vertical stack of shelves. Same idea as [TvRowRecyclerView]: the focused
 * row parks at a fixed height and the page moves around it.
 */
class TvColumnRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        layoutManager = object : LinearLayoutManager(context, VERTICAL, false) {
            override fun getExtraLayoutSpace(state: State) = height / 2
        }
        itemAnimator = null
        isFocusable = false
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        clipChildren = false
        clipToPadding = false
        overScrollMode = OVER_SCROLL_NEVER
        setItemViewCacheSize(4)
    }

    override fun requestChildRectangleOnScreen(
        child: View,
        rect: Rect,
        immediate: Boolean
    ): Boolean {
        val dy = child.top - paddingTop
        if (dy == 0) return false
        if (immediate) {
            scrollBy(0, dy)
        } else {
            smoothScrollBy(0, dy, Motion.decelerate, Motion.ROW_SCROLL_MS.toInt())
        }
        return true
    }
}
