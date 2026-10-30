package org.fossify.gallery.helpers

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * En-tête qui s'efface au défilement (logo + nom) et pastille de date façon Google Photos
 * (« Sam. 24 oct. ») affichée en haut pendant qu'on fait défiler la grille.
 */
object NpScrollUi {
    private val sameYear = SimpleDateFormat("EEE d MMM", Locale.getDefault())
    private val otherYear = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())

    fun label(ts: Long): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        cal.timeInMillis = ts
        val text = (if (cal.get(Calendar.YEAR) == year) sameYear else otherYear).format(cal.time)
        return text.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    fun attach(grid: RecyclerView, brand: View?, pill: TextView?, dateAt: ((Int) -> Long?)?) {
        val handler = Handler(Looper.getMainLooper())
        val hidePill = Runnable {
            pill?.animate()?.alpha(0f)?.translationY(-8 * grid.resources.displayMetrics.density)?.setDuration(220)
                ?.withEndAction { pill.visibility = View.INVISIBLE }?.start()
        }
        var brandShown = true

        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                // logo et nom : seulement tout en haut de la grille
                val atTop = !rv.canScrollVertically(-1)
                if (brand != null && atTop != brandShown) {
                    brandShown = atTop
                    brand.animate().cancel()
                    brand.animate()
                        .alpha(if (atTop) 1f else 0f)
                        .translationY(if (atTop) 0f else -10 * rv.resources.displayMetrics.density)
                        .setDuration(180).start()
                }

                if (pill == null || dateAt == null || dy == 0 || rv.scrollState == RecyclerView.SCROLL_STATE_IDLE) return
                val first = (rv.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: return
                if (first == RecyclerView.NO_POSITION) return
                val ts = dateAt(first) ?: return
                val text = label(ts)
                if (pill.text != text) pill.text = text
                if (atTop) {
                    handler.removeCallbacks(hidePill)
                    hidePill.run()
                    return
                }
                if (pill.visibility != View.VISIBLE || pill.alpha < 1f) {
                    pill.animate().cancel()
                    if (pill.visibility != View.VISIBLE) {
                        pill.alpha = 0f
                        pill.translationY = -8 * rv.resources.displayMetrics.density
                        pill.visibility = View.VISIBLE
                    }
                    pill.animate().alpha(1f).translationY(0f).setDuration(160).start()
                }
                handler.removeCallbacks(hidePill)
                handler.postDelayed(hidePill, 1400)
            }

            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    handler.removeCallbacks(hidePill)
                    handler.postDelayed(hidePill, 900)
                } else {
                    handler.removeCallbacks(hidePill)
                }
            }
        })
    }

    /** Remet le logo à sa place (changement d'onglet, retour en haut). */
    fun resetBrand(brand: View?) {
        brand?.animate()?.cancel()
        brand?.alpha = 1f
        brand?.translationY = 0f
    }
}
