package com.bcb58.mialauncher.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bcb58.mialauncher.R
import com.bcb58.mialauncher.data.AppEntry
import com.bcb58.mialauncher.data.AppRow
import com.bcb58.mialauncher.data.Artwork

/**
 * Renders one shelf of cards.
 *
 * The expensive part — compositing artwork — never happens on the main thread
 * and never happens twice for the same app. Each holder carries a generation
 * counter so a bitmap that arrives after the holder was recycled is dropped
 * instead of flashing the wrong app's art.
 */
class AppCardAdapter(
    private val context: Context,
    private val style: AppRow.Style,
    private val onClick: (AppEntry, View) -> Unit,
    private val onLongClick: (AppEntry, View) -> Unit,
    private val onFocus: (AppEntry, View) -> Unit
) : RecyclerView.Adapter<AppCardAdapter.Holder>() {

    private var items: List<AppEntry> = emptyList()

    private val cardWidth = context.resources.getDimensionPixelSize(
        if (style == AppRow.Style.BANNER) R.dimen.card_banner_width else R.dimen.card_tile_width
    )
    private val cardHeight = context.resources.getDimensionPixelSize(
        if (style == AppRow.Style.BANNER) R.dimen.card_banner_height else R.dimen.card_tile_height
    )
    private val gap = context.resources.getDimensionPixelSize(R.dimen.card_gap)

    init {
        setHasStableIds(true)
    }

    fun submit(newItems: List<AppEntry>) {
        if (items == newItems) return
        val old = items
        val diff = androidx.recyclerview.widget.DiffUtil.calculateDiff(
            object : androidx.recyclerview.widget.DiffUtil.Callback() {
                override fun getOldListSize() = old.size
                override fun getNewListSize() = newItems.size
                override fun areItemsTheSame(o: Int, n: Int) = old[o].id == newItems[n].id
                override fun areContentsTheSame(o: Int, n: Int) = old[o] == newItems[n]
            },
            false
        )
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemId(position: Int): Long = items[position].stableId

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_card, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun onViewRecycled(holder: Holder) {
        holder.recycle()
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: FocusCardView = view.findViewById(R.id.card)
        private val art: ImageView = view.findViewById(R.id.art)
        private val label: TextView = view.findViewById(R.id.label)

        /** Bumped on every (re)bind; async artwork compares against it. */
        private var generation = 0
        private var entry: AppEntry? = null

        init {
            card.layoutParams = card.layoutParams.apply {
                width = cardWidth
                height = cardHeight
            }
            label.layoutParams = label.layoutParams.apply { width = cardWidth }
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd = gap

            card.setOnClickListener { entry?.let { e -> onClick(e, card) } }
            card.setOnLongClickListener {
                entry?.let { e -> onLongClick(e, card) }
                true
            }
            card.setOnFocusChangeListener { _, hasFocus ->
                label.animate()
                    .alpha(if (hasFocus) 1f else 0.5f)
                    .translationY(if (hasFocus) 2f else 0f)
                    .setDuration(Motion.FOCUS_IN_MS)
                    .setInterpolator(Motion.standard)
                    .start()
                if (hasFocus) entry?.let { e -> onFocus(e, card) }
            }
        }

        fun bind(item: AppEntry) {
            entry = item
            generation++
            val myGeneration = generation

            label.text = item.label
            card.contentDescription = context.getString(R.string.cd_app_card, item.label)
            card.tiltEnabled = com.bcb58.mialauncher.data.Prefs.get(context).parallaxFocus

            val key = Artwork.keyFor(item, cardWidth, cardHeight)
            val memoryHit: Bitmap? = Artwork.cached(key)
            if (memoryHit != null) {
                // Synchronous path: no fade, so a scrolled-back row looks static.
                art.setImageBitmap(memoryHit)
                art.alpha = 1f
                return
            }

            art.setImageDrawable(null)
            art.alpha = 0f
            Artwork.load(
                context = context,
                entry = item,
                w = cardWidth,
                h = cardHeight,
                onReady = { bitmap, _ ->
                    art.post {
                        if (generation != myGeneration) return@post
                        art.setImageBitmap(bitmap)
                        art.animate().alpha(1f)
                            .setDuration(180L)
                            .setInterpolator(Motion.standard)
                            .start()
                    }
                },
                onCancelled = { generation != myGeneration }
            )
        }

        fun recycle() {
            generation++
            card.resetVisuals()
            art.animate().cancel()
            art.setImageDrawable(null)
            art.alpha = 0f
            label.animate().cancel()
            label.alpha = 0.5f
            label.translationY = 0f
            entry = null
        }
    }
}
