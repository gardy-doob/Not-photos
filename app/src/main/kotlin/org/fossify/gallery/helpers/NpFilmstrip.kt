package org.fossify.gallery.helpers

import android.graphics.Outline
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.fossify.gallery.R

/**
 * Bande de miniatures façon iOS : on la fait défiler au doigt, la miniature du centre
 * devient la photo affichée en grand. Un espace marque le début de chaque jalon.
 */
class NpFilmstrip(
    private val recyclerView: RecyclerView,
    private val onCenterChanged: (position: Int) -> Unit
) {
    private val density = recyclerView.resources.displayMetrics.density
    private val itemWidth = (36 * density).toInt()
    private val itemHeight = (48 * density).toInt()
    private val itemSpacing = (2 * density).toInt()
    private val gapWidth = (14 * density).toInt()

    private val layoutManager = LinearLayoutManager(recyclerView.context, LinearLayoutManager.HORIZONTAL, false)
    private val snapHelper = LinearSnapHelper()
    private val adapter = StripAdapter()

    private var paths: List<String> = emptyList()
    private var gapsBefore: Set<Int> = emptySet()
    private var current = -1
    private var userScrolling = false

    init {
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = null
        snapHelper.attachToRecyclerView(recyclerView)
        recyclerView.addOnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                val side = (v.width - itemWidth) / 2 - itemSpacing
                v.setPadding(side.coerceAtLeast(0), 0, side.coerceAtLeast(0), 0)
                if (current >= 0) scrollToCenter(current)
            }
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> userScrolling = true
                    RecyclerView.SCROLL_STATE_IDLE -> userScrolling = false
                }
            }

            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!userScrolling) return
                val center = centerPosition()
                if (center != RecyclerView.NO_POSITION && center != current) {
                    val previous = current
                    current = center
                    if (previous >= 0) adapter.notifyItemChanged(previous)
                    adapter.notifyItemChanged(center)
                    rv.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onCenterChanged(center)
                }
            }
        })
    }

    fun setMedia(newPaths: List<String>, newGapsBefore: Set<Int>) {
        if (newPaths == paths && newGapsBefore == gapsBefore) return
        paths = newPaths
        gapsBefore = newGapsBefore
        adapter.notifyDataSetChanged()
        if (current >= 0) recyclerView.post { scrollToCenter(current) }
    }

    /** Appelé quand la photo affichée change autrement (balayage de la photo). */
    fun setCurrent(position: Int) {
        if (position < 0 || position >= paths.size) return
        val previous = current
        current = position
        if (previous >= 0 && previous < paths.size) adapter.notifyItemChanged(previous)
        adapter.notifyItemChanged(position)
        if (!userScrolling) recyclerView.post { scrollToCenter(position) }
    }

    private fun centerPosition(): Int {
        val snapView = snapHelper.findSnapView(layoutManager) ?: return RecyclerView.NO_POSITION
        return layoutManager.getPosition(snapView)
    }

    private fun scrollToCenter(position: Int) {
        layoutManager.scrollToPositionWithOffset(position, 0)
        recyclerView.post {
            val view = layoutManager.findViewByPosition(position) ?: return@post
            val distance = snapHelper.calculateDistanceToFinalSnap(layoutManager, view) ?: return@post
            if (distance[0] != 0) recyclerView.scrollBy(distance[0], 0)
        }
    }

    private inner class StripAdapter : RecyclerView.Adapter<StripHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StripHolder {
            val context = parent.context
            val frame = FrameLayout(context)
            frame.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, itemHeight)

            val gap = View(context).apply {
                layoutParams = FrameLayout.LayoutParams((3 * density).toInt(), (56 * density).toInt()).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                setBackgroundColor(0xFFFF6B7E.toInt())
            }

            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, 6 * density)
                    }
                }
            }
            frame.addView(gap)
            frame.addView(image)
            return StripHolder(frame, image, gap)
        }

        override fun onBindViewHolder(holder: StripHolder, position: Int) {
            val hasGap = gapsBefore.contains(position) && position > 0
            val startOffset = if (hasGap) gapWidth else 0
            (holder.image.layoutParams as FrameLayout.LayoutParams).apply {
                width = itemWidth
                height = itemHeight
                leftMargin = startOffset
            }
            holder.gap.visibility = if (hasGap) View.VISIBLE else View.GONE
            (holder.gap.layoutParams as FrameLayout.LayoutParams).leftMargin = (gapWidth - 3 * density).toInt() / 2
            (holder.itemView.layoutParams as RecyclerView.LayoutParams).apply {
                width = itemWidth + startOffset
                leftMargin = itemSpacing
                rightMargin = itemSpacing
            }

            val isCurrent = position == current
            holder.image.alpha = if (isCurrent) 1f else 0.6f
            holder.image.foreground = if (isCurrent) {
                ContextCompat.getDrawable(holder.itemView.context, R.drawable.np_strip_current_fg)
            } else {
                null
            }

            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onCenterChanged(pos)
                setCurrent(pos)
            }

            Glide.with(holder.image)
                .load(paths[position])
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .override(itemWidth * 2, itemHeight * 2)
                .centerCrop()
                .into(holder.image)
        }

        override fun getItemCount() = paths.size
    }

    private class StripHolder(view: View, val image: ImageView, val gap: View) : RecyclerView.ViewHolder(view)
}
