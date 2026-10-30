package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Barre « liquide » (brief nav-liquide) : la barre, la bulle Souvenirs et une goutte de traîne sont
 * dessinées ensemble puis floutées et seuillées (effet gooey) : elles fusionnent comme du liquide.
 * Le mouvement est piloté par des ressorts physiques (tension 265, amortissement 27, 4 sous-pas par image).
 *
 * La bulle « fermée » est cachée sous l'icône Galerie ; « ouverte », elle est à la place de [toggle].
 */
class NpGoo(
    private val nav: FrameLayout,
    private val column: View,
    private val pill: View,
    private val toggle: View,
    private val anchor: View,
    private val color: Int
) {
    private val density = nav.resources.displayMetrics.density
    private val shadow = Layer(nav.context, true)
    private val goo = Layer(nav.context, false)

    // réglages validés du brief
    private val k = 265f
    private val c = 27f
    private val closedR = 10f
    private val openR get() = toggle.width / 2f / density

    private var open = false
    private var x = 0f      // progression le long du trajet, en dp (0 = fermée, D = ouverte)
    private var vx = 0f
    private var r = closedR
    private var vr = 0f
    private var tx = 0f     // goutte de traîne
    private var tvx = 0f
    private var running = false
    private var lastNanos = 0L
    private var horizontal = false

    init {
        nav.addView(shadow, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        nav.addView(goo, 1, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        shadow.translationY = 3 * density
        pill.background = null
        pill.elevation = 0f
        toggle.elevation = 0f
        nav.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> render() }
    }

    fun setHorizontal(value: Boolean) {
        horizontal = value
        render()
    }

    private fun distance(): Float {
        val (cx, cy) = closedCenter()
        val (ox, oy) = openCenter()
        return hypot(ox - cx, oy - cy) / density
    }

    private fun openCenter(): Pair<Float, Float> =
        (column.left + toggle.left + toggle.width / 2f) to (column.top + toggle.top + toggle.height / 2f)

    private fun closedCenter(): Pair<Float, Float> =
        (column.left + pill.left + anchor.left + anchor.width / 2f) to (column.top + pill.top + anchor.top + anchor.height / 2f)

    /** Sortir ou rentrer la bulle. */
    fun setOpen(value: Boolean, animate: Boolean, delayMs: Long = 0L) {
        if (!animate) {
            open = value
            val d = distance()
            x = if (value) d else 0f
            tx = x
            r = if (value) openR.takeIf { it > 0 } ?: 25f else closedR
            vx = 0f; vr = 0f; tvx = 0f
            if (toggle.width == 0) toggle.post { setOpen(open, false) }
            render()
            return
        }
        if (delayMs > 0) {
            nav.postDelayed({ setOpen(value, true) }, delayMs)
            return
        }
        if (open == value) return
        open = value
        vr += if (value) 60f else -40f
        kick()
    }

    fun isOpen() = open

    /** Appui sur la bulle déjà sortie : impulsion sur le rayon (effet gelée). */
    fun jelly() {
        vr -= 260f
        kick()
    }

    private fun kick() {
        if (running) return
        running = true
        lastNanos = System.nanoTime()
        nav.postOnAnimation(frame)
    }

    private val frame = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            val elapsed = min(0.05f, (now - lastNanos) / 1e9f).takeIf { it > 0f } ?: 0.016f
            lastNanos = now
            repeat(4) { step(elapsed / 4) }
            render()
            val tX = if (open) distance() else 0f
            val tR = if (open) openR else closedR
            val settled = listOf(x - tX, vx, r - tR, vr, tx - x, tvx).all { abs(it) < 0.05f }
            if (settled) {
                x = tX; r = tR; tx = x; vx = 0f; vr = 0f; tvx = 0f
                render()
                running = false
            } else {
                nav.postOnAnimation(this)
            }
        }
    }

    private fun step(dt: Float) {
        val tX = if (open) distance() else 0f
        val tR = if (open) openR else closedR
        vx += (-k * (x - tX) - c * vx) * dt; x += vx * dt
        val kr = if (open) k * 1.25f else k * 0.8f
        vr += (-kr * (r - tR) - c * 1.05f * vr) * dt; r += vr * dt
        tvx += (-(k * 0.45f) * (tx - x) - (c * 0.85f) * tvx) * dt; tx += tvx * dt
    }

    private fun pointAt(progressDp: Float): Pair<Float, Float> {
        val (cx, cy) = closedCenter()
        val (ox, oy) = openCenter()
        val d = distance()
        val t = if (d > 0f) progressDp / d else 0f
        return (cx + (ox - cx) * t) to (cy + (oy - cy) * t)
    }

    /** Place l'icône de la bulle sur la goutte et redessine. */
    fun render() {
        if (toggle.width == 0) return
        val d = distance()
        val radius = max(2f, r)
        val (bx, by) = pointAt(x)
        val (ox, oy) = openCenter()
        val p = if (d > 0f) (x / d).coerceIn(0f, 1f) else 0f
        val vis = ((radius - 18f) / 7f).coerceIn(0f, 1f)
        toggle.translationX = bx - ox
        toggle.translationY = by - oy
        val scale = (0.4f + 0.6f * vis) * (radius / max(openR, 1f)).coerceIn(0.5f, 1.3f)
        toggle.scaleX = scale
        toggle.scaleY = scale
        toggle.rotation = (1 - p) * -90f
        toggle.alpha = vis
        toggle.isClickable = vis > 0.6f
        toggle.visibility = if (vis > 0.01f) View.VISIBLE else View.INVISIBLE
        goo.invalidate()
        shadow.invalidate()
    }

    private inner class Layer(context: Context, private val isShadow: Boolean) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            paint.color = if (isShadow) 0x33140E0C else color
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blur = RenderEffect.createBlurEffect(
                    (if (isShadow) 9f else 6f) * density, (if (isShadow) 9f else 6f) * density, Shader.TileMode.DECAL
                )
                setRenderEffect(
                    if (isShadow) blur else {
                        // seuil alpha « 0 0 0 22 -10 » : les bords flous redeviennent nets, les formes proches fusionnent
                        val m = ColorMatrix(
                            floatArrayOf(
                                1f, 0f, 0f, 0f, 0f,
                                0f, 1f, 0f, 0f, 0f,
                                0f, 0f, 1f, 0f, 0f,
                                0f, 0f, 0f, 22f, -10f * 255f
                            )
                        )
                        RenderEffect.createChainEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(m)), blur)
                    }
                )
            }
        }

        override fun onDraw(canvas: Canvas) {
            if (pill.width == 0) return
            rect.set(
                (column.left + pill.left).toFloat(), (column.top + pill.top).toFloat(),
                (column.left + pill.right).toFloat(), (column.top + pill.bottom).toFloat()
            )
            val corner = min(rect.width(), rect.height()) / 2f
            canvas.drawRoundRect(rect, corner, corner, paint)

            val radius = max(2f, r) * density
            val stretch = min(0.28f, abs(vx) / 2000f)
            val along = radius * (1 + stretch)
            val across = radius * (1 - stretch * 0.6f)
            val (bx, by) = pointAt(x)
            val (rx, ry) = if (horizontal) along to across else across to along
            canvas.drawOval(bx - rx, by - ry, bx + rx, by + ry, paint)

            val tr = radius * 0.55f
            val (tx0, ty0) = pointAt(tx)
            val (trx, try0) = if (horizontal) tr to tr * (1 - stretch * 0.4f) else tr * (1 - stretch * 0.4f) to tr
            canvas.drawOval(tx0 - trx, ty0 - try0, tx0 + trx, ty0 + try0, paint)
        }
    }

    companion object {
        fun of(nav: View): NpGoo? = nav.getTag(org.fossify.gallery.R.id.np_nav_goo) as? NpGoo
    }
}
