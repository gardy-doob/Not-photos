package org.fossify.gallery.helpers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView

/**
 * Dessine les jalons derrière la grille :
 *  - Frise : une ligne verticale à gauche et un point rouge à chaque début de jalon
 *  - Cartes : une carte arrondie qui regroupe l'en-tête et les photos du jalon
 */
class NpJalonDecoration(
    private val styleOf: () -> Int,
    private val enabled: () -> Boolean,
    private val background: () -> Int
) : RecyclerView.ItemDecoration() {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB3263A.toInt() }
    private val dotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (!enabled()) return
        val ranges = NpJalons.lastRanges
        if (ranges.isEmpty()) return
        val density = parent.resources.displayMetrics.density
        val bg = background()
        val isDark = ColorUtils.calculateLuminance(bg) < 0.4

        // bornes visibles de chaque jalon
        val tops = HashMap<Int, Float>()
        val bottoms = HashMap<Int, Float>()
        val headerCenter = HashMap<Int, Float>()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION) continue
            val r = ranges.indexOfFirst { pos in it }
            if (r < 0) continue
            val top = child.top + child.translationY
            val bottom = child.bottom + child.translationY
            tops[r] = minOf(tops[r] ?: Float.MAX_VALUE, top)
            bottoms[r] = maxOf(bottoms[r] ?: -Float.MAX_VALUE, bottom)
            if (pos == ranges[r].first) headerCenter[r] = top + child.paddingTop + 14 * density
        }

        if (styleOf() == NpSettings.STYLE_CARDS) {
            cardPaint.color = if (isDark) ColorUtils.blendARGB(bg, Color.WHITE, 0.07f) else ColorUtils.blendARGB(bg, Color.BLACK, 0.045f)
            val radius = 22 * density
            val left = parent.paddingLeft - 6 * density
            val right = parent.width - parent.paddingRight + 6 * density
            tops.keys.forEach { r ->
                val first = ranges[r].first
                val top = if (headerCenter.containsKey(r)) tops[r]!! + 2 * density else -radius
                val last = ranges[r].last
                val lastVisible = (0 until parent.childCount).any { parent.getChildAdapterPosition(parent.getChildAt(it)) == last }
                val bottom = if (lastVisible) bottoms[r]!! + 8 * density else parent.height + radius
                if (first <= last) {
                    rect.set(left, top, right, bottom)
                    c.drawRoundRect(rect, radius, radius, cardPaint)
                }
            }
        } else {
            val x = parent.paddingLeft - 16 * density
            linePaint.color = if (isDark) 0x55FFFFFF else 0x33000000
            linePaint.strokeWidth = 2 * density
            dotRingPaint.color = bg
            dotRingPaint.strokeWidth = 3 * density
            tops.keys.forEach { r ->
                val last = ranges[r].last
                val lastVisible = (0 until parent.childCount).any { parent.getChildAdapterPosition(parent.getChildAt(it)) == last }
                val top = headerCenter[r] ?: 0f
                val bottom = if (lastVisible) bottoms[r]!! - 4 * density else parent.height.toFloat()
                if (bottom > top) c.drawLine(x, top, x, bottom, linePaint)
                headerCenter[r]?.let { cy ->
                    c.drawCircle(x, cy, 8 * density, dotRingPaint)
                    c.drawCircle(x, cy, 7 * density, dotPaint)
                }
            }
        }
    }
}
