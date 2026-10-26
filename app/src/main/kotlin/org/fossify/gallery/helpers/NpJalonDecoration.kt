package org.fossify.gallery.helpers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView

/**
 * Dessine les souvenirs derrière la grille :
 *  - Cartes (maquette « Souvenirs ») : fond noir, photos libres en 6 colonnes, chaque souvenir
 *    dans une carte blanche arrondie en 3 colonnes. Cette décoration gère alors tous les espacements.
 *  - Frise : une ligne verticale à gauche et un point rouge à chaque début de souvenir.
 */
class NpJalonDecoration(
    private val styleOf: () -> Int,
    private val enabled: () -> Boolean,
    private val background: () -> Int
) : RecyclerView.ItemDecoration() {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB3263A.toInt() }
    private val dotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    private val rect = RectF()

    private fun cards() = enabled() && styleOf() == NpSettings.STYLE_CARDS

    // espacements (dp) repris de la maquette
    private val freeEdge = 13f
    private val freeGap = 4f
    private val cardEdge = 13f + 12f
    private val cardGap = 6f

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (!cards()) return
        val pos = parent.getChildAdapterPosition(view)
        val layout = NpJalons.lastLayout
        if (pos == RecyclerView.NO_POSITION || pos >= layout.size) return
        val d = parent.resources.displayMetrics.density
        val code = layout[pos]
        when {
            code < 0 -> {
                val isCardHeader = NpJalons.lastRanges.any { it.first == pos }
                if (isCardHeader) outRect.set((cardEdge * d).toInt(), (16 * d).toInt(), (cardEdge * d).toInt(), (4 * d).toInt())
            }

            code >= 10 -> setColumn(outRect, code - 10, 3, cardEdge * d, cardGap * d, pos, d)
            else -> setColumn(outRect, code, 6, freeEdge * d, freeGap * d, pos, d)
        }
    }

    /** Colonnes de même largeur avec une marge extérieure [edge] et un écart [gap]. */
    private fun setColumn(outRect: Rect, col: Int, n: Int, edge: Float, gap: Float, pos: Int, d: Float) {
        val total = (2 * edge + (n - 1) * gap) / n
        val left = edge - col * (total - gap)
        outRect.left = left.toInt()
        outRect.right = (total - left).toInt()
        outRect.bottom = gap.toInt()
        val isLastOfCard = NpJalons.lastRanges.any { it.last == pos }
        if (isLastOfCard) outRect.bottom = (gap + 14 * d).toInt()
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (!enabled()) return
        val ranges = NpJalons.lastRanges
        if (ranges.isEmpty()) return
        val density = parent.resources.displayMetrics.density
        val bg = background()
        val isDark = ColorUtils.calculateLuminance(bg) < 0.4

        val tops = HashMap<Int, Float>()
        val bottoms = HashMap<Int, Float>()
        val headerCenter = HashMap<Int, Float>()
        val visibleLast = HashSet<Int>()
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
            if (pos == ranges[r].last) visibleLast.add(r)
        }

        if (styleOf() == NpSettings.STYLE_CARDS) {
            val radius = 24 * density
            val left = 13 * density
            val right = parent.width - 13 * density
            tops.keys.forEach { r ->
                val top = if (headerCenter.containsKey(r)) tops[r]!! - 4 * density else -radius
                val bottom = if (r in visibleLast) bottoms[r]!! + 12 * density else parent.height + radius
                rect.set(left, top + 3 * density, right, bottom + 5 * density)
                c.drawRoundRect(rect, radius, radius, shadowPaint)
                rect.set(left, top, right, bottom)
                c.drawRoundRect(rect, radius, radius, cardPaint)
            }
        } else {
            val x = parent.paddingLeft - 16 * density
            linePaint.color = if (isDark) 0x55FFFFFF else 0x33000000
            linePaint.strokeWidth = 2 * density
            dotRingPaint.color = bg
            dotRingPaint.strokeWidth = 3 * density
            tops.keys.forEach { r ->
                val top = headerCenter[r] ?: 0f
                val bottom = if (r in visibleLast) bottoms[r]!! - 4 * density else parent.height.toFloat()
                if (bottom > top) c.drawLine(x, top, x, bottom, linePaint)
                headerCenter[r]?.let { cy ->
                    c.drawCircle(x, cy, 8 * density, dotRingPaint)
                    c.drawCircle(x, cy, 7 * density, dotPaint)
                }
            }
        }
    }
}
