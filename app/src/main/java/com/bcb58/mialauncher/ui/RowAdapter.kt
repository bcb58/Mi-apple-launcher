package com.bcb58.mialauncher.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bcb58.mialauncher.R
import com.bcb58.mialauncher.data.AppEntry
import com.bcb58.mialauncher.data.AppRow

/** The vertical stack of shelves on the home screen. */
class RowAdapter(
    private val context: Context,
    private val onClick: (AppEntry, View) -> Unit,
    private val onLongClick: (AppEntry, View) -> Unit,
    private val onFocus: (AppEntry, View) -> Unit
) : RecyclerView.Adapter<RowAdapter.RowHolder>() {

    private var rows: List<AppRow> = emptyList()

    /** One pool for every shelf: cards are interchangeable, so reuse is high. */
    private val sharedPool = RecyclerView.RecycledViewPool().apply {
        setMaxRecycledViews(0, 24)
    }

    /** Remembers each shelf's scroll offset across data changes. */
    private val scrollOffsets = HashMap<String, Int>()

    init {
        setHasStableIds(true)
    }

    fun submit(newRows: List<AppRow>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = rows.size
            override fun getNewListSize() = newRows.size
            override fun areItemsTheSame(o: Int, n: Int) = rows[o].key == newRows[n].key
            override fun areContentsTheSame(o: Int, n: Int) = rows[o] == newRows[n]
        }, false)
        rows = newRows
        diff.dispatchUpdatesTo(this)
    }

    fun rowAt(position: Int): AppRow? = rows.getOrNull(position)

    override fun getItemId(position: Int): Long = rows[position].key.hashCode().toLong()

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_row, parent, false)
        return RowHolder(view)
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        holder.bind(rows[position])
    }

    override fun onViewRecycled(holder: RowHolder) {
        holder.saveScroll()
    }

    inner class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.rowTitle)
        private val list: TvRowRecyclerView = view.findViewById(R.id.rowItems)
        private var key: String? = null

        init {
            list.setRecycledViewPool(sharedPool)
        }

        fun bind(row: AppRow) {
            key = row.key
            title.text = row.title

            val existing = list.adapter as? AppCardAdapter
            if (existing != null && list.getTag(R.id.rowItems) == row.style) {
                existing.submit(row.items)
            } else {
                val adapter = AppCardAdapter(context, row.style, onClick, onLongClick, onFocus)
                adapter.submit(row.items)
                list.setTag(R.id.rowItems, row.style)
                list.adapter = adapter
            }

            val offset = scrollOffsets[row.key] ?: 0
            list.scrollToPosition(0)
            if (offset > 0) list.post { list.scrollBy(offset, 0) }
        }

        fun saveScroll() {
            key?.let { scrollOffsets[it] = list.computeHorizontalScrollOffset() }
        }
    }
}
