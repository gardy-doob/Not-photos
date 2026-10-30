package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import org.fossify.gallery.R
import org.fossify.gallery.activities.JalonsActivity
import org.fossify.gallery.activities.MainActivity
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.extensions.config
import java.io.File

/** Onglet courant de la barre flottante Not Photos. */
const val NP_TAB = "np_tab"
const val NP_TAB_GALLERY = "gallery"
const val NP_TAB_VIDEOS = "videos"
const val NP_TAB_JALONS = "jalons"
const val NP_TAB_ALL = "all"
const val NP_TAB_FOLDERS = "folders"

/** Ne garder que les vidéos du dossier affiché. */
const val NP_ONLY_VIDEOS = "np_only_videos"

/** Afficher tous les dossiers inclus, quel que soit le réglage global. */
const val NP_FORCE_SHOW_ALL = "np_force_show_all"

/** Faire défiler la Galerie jusqu'à cette photo à l'ouverture (depuis l'onglet Jalons). */
const val NP_SCROLL_TO = "np_scroll_to"

/** Faire apparaître la grille en cascade à l'ouverture. */
const val NP_CASCADE = "np_cascade"

object NpNav {
    private val tabs = listOf(NP_TAB_GALLERY, NP_TAB_VIDEOS, NP_TAB_JALONS, NP_TAB_ALL, NP_TAB_FOLDERS)

    /** Dossier de l'appareil photo, ou "" s'il n'existe pas. */
    fun cameraPath(context: Context): String {
        val base = context.config.internalStoragePath.trimEnd('/')
        val candidates = listOf("$base/DCIM/Camera", "/storage/emulated/0/DCIM/Camera", "$base/DCIM")
        return candidates.firstOrNull { File(it).isDirectory } ?: ""
    }

    fun intentFor(context: Context, tab: String): Intent {
        val intent = when (tab) {
            NP_TAB_FOLDERS -> Intent(context, MainActivity::class.java)
            NP_TAB_JALONS -> Intent(context, JalonsActivity::class.java)
            // Galerie, Vidéos et Toutes chargent tous les dossiers ; le tri par onglet se fait à l'affichage
            else -> Intent(context, org.fossify.gallery.activities.GalleryActivity::class.java).apply {
                putExtra(DIRECTORY, "")
                putExtra(NP_FORCE_SHOW_ALL, true)
                if (tab == NP_TAB_VIDEOS) putExtra(NP_ONLY_VIDEOS, true)
            }
        }
        intent.putExtra(NP_TAB, tab)
        intent.putExtra(SKIP_AUTHENTICATION, true)
        return intent
    }

    /** Change d'onglet : une seule activité dans la pile, avec un fondu. */
    fun go(activity: Activity, tab: String) {
        // les onglets restent ouverts : on ramène l'écran existant au premier plan au lieu de le recréer
        val intent = intentFor(activity, tab).apply {
            putExtra(NP_CASCADE, true)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        activity.startActivity(intent)
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    /** Onglets de la barre verticale, de haut en bas (les Vidéos sont un filtre de l'en-tête). */
    private val railTabs = listOf(NP_TAB_GALLERY, NP_TAB_JALONS, NP_TAB_ALL, NP_TAB_FOLDERS)

    private fun railLabel(context: Context, tab: String) = context.getString(
        when (tab) {
            NP_TAB_GALLERY -> R.string.np_gallery
            NP_TAB_JALONS -> R.string.np_jalons
            NP_TAB_ALL -> R.string.np_all
            else -> R.string.np_folders
        }
    )

    /** Onglets où les Souvenirs existent : la Galerie (photos) et son filtre Vidéos. */
    fun souvenirsTab(tab: String) = tab == NP_TAB_GALLERY || tab == NP_TAB_VIDEOS

    /**
     * Place la barre selon le réglage : verticale à droite, un peu au-dessus du milieu bas de l'écran
     * (sous le pouce), ou horizontale centrée en bas.
     */
    fun applyLayout(activity: Activity) {
        val nav = activity.findViewById<View>(R.id.np_nav) ?: return
        val column = nav.findViewById<android.widget.LinearLayout>(R.id.np_nav_column) ?: return
        val pill = nav.findViewById<android.widget.LinearLayout>(R.id.np_nav_pill) ?: return
        val label = nav.findViewById<View>(R.id.np_nav_label)
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val horizontal = NpSettings.navHorizontal(activity)
        val orientation = if (horizontal) android.widget.LinearLayout.HORIZONTAL else android.widget.LinearLayout.VERTICAL

        column.orientation = orientation
        pill.orientation = orientation
        (column.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let {
            if (horizontal) {
                it.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                it.marginStart = 0
                it.topMargin = dp(48)
                it.bottomMargin = 0
            } else {
                it.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                it.marginStart = dp(140)
                it.topMargin = 0
                // plus haut que le bas de l'écran : à hauteur du pouce
                it.bottomMargin = (activity.resources.displayMetrics.heightPixels * 0.16f).toInt()
            }
            column.layoutParams = it
        }
        pill.layoutParams = pill.layoutParams.apply {
            width = if (horizontal) android.view.ViewGroup.LayoutParams.WRAP_CONTENT else dp(56)
            height = if (horizontal) dp(56) else android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        }
        for (i in 0 until pill.childCount) {
            val item = pill.getChildAt(i)
            (item.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let {
                it.gravity = android.view.Gravity.CENTER
                if (horizontal) it.setMargins(dp(2), 0, dp(2), 0) else it.setMargins(0, dp(2), 0, dp(2))
                item.layoutParams = it
            }
        }
        // marge autour de la barre : le flou de l'effet liquide ne doit pas être coupé au bord
        (column.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let {
            it.marginEnd = dp(12)
            if (horizontal) it.marginStart = dp(12)
            column.layoutParams = it
        }
        NpGoo.of(nav)?.setHorizontal(horizontal)
        (label?.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let {
            it.gravity = if (horizontal) android.view.Gravity.TOP or android.view.Gravity.START else android.view.Gravity.TOP or android.view.Gravity.END
            it.marginEnd = if (horizontal) 0 else dp(80)
            label.layoutParams = it
        }
        (nav.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)?.let {
            it.gravity = if (horizontal) android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL else android.view.Gravity.BOTTOM or android.view.Gravity.END
            it.marginEnd = 0
            nav.layoutParams = it
        }
    }

    /** Barre liquide de cet écran (créée une seule fois). */
    private fun gooOf(activity: Activity): NpGoo? {
        val nav = activity.findViewById<android.widget.FrameLayout>(R.id.np_nav) ?: return null
        NpGoo.of(nav)?.let { return it }
        val goo = NpGoo(
            nav = nav,
            column = nav.findViewById(R.id.np_nav_column),
            pill = nav.findViewById(R.id.np_nav_pill),
            toggle = nav.findViewById(R.id.np_nav_toggle),
            anchor = nav.findViewById(R.id.np_nav_gallery),
            color = 0xFFFCEDE6.toInt()
        )
        goo.setHorizontal(NpSettings.navHorizontal(activity))
        nav.setTag(R.id.np_nav_goo, goo)
        return goo
    }

    /**
     * Fait sortir (ou rentrer) la bulle Souvenirs de « Galerie », comme une goutte.
     * [delayMs] : à l'ouverture de l'appli, la bulle sort un instant après l'affichage.
     */
    fun showSouvenirBubble(activity: Activity, show: Boolean, animate: Boolean, delayMs: Long = 0L) {
        val goo = gooOf(activity) ?: return
        goo.setOpen(show, animate, delayMs)
    }

    /**
     * Barre sous le pouce : des icônes seules ; en posant le doigt, le nom de la vue apparaît
     * (à gauche, ou au-dessus en horizontal) et on peut glisser pour en choisir une autre, puis relâcher.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun setup(
        activity: Activity,
        current: String,
        onReselect: (() -> Unit)? = null,
        onSelect: ((String) -> Boolean)? = null,
        onToggleJalons: (() -> Unit)? = null,
        animateBubble: Boolean = false
    ) {
        val nav = activity.findViewById<View>(R.id.np_nav) ?: return
        applyLayout(activity)
        val shownTab = if (current == NP_TAB_VIDEOS) NP_TAB_GALLERY else current

        // bulle Souvenirs : en négatif quand le mode est actif, seulement dans la Galerie
        val toggle = nav.findViewById<ImageView>(R.id.np_nav_toggle)
        fun paintToggle() {
            val on = NpSettings.showJalons(activity)
            if (on) toggle?.setBackgroundResource(R.drawable.np_nav_bubble_inner) else toggle?.background = null
            toggle?.setColorFilter(if (on) 0xFFFFFFFF.toInt() else 0xFF3A3330.toInt())
        }
        paintToggle()
        val goo = gooOf(activity)
        if (goo != null && nav.getTag(R.id.np_nav_toggle) == null) {
            // premier affichage : la bulle sort une seconde après l'ouverture
            nav.setTag(R.id.np_nav_toggle, true)
            goo.setOpen(false, false)
            if (souvenirsTab(current)) goo.setOpen(true, true, 1000L)
        } else {
            showSouvenirBubble(activity, souvenirsTab(current), animateBubble)
        }
        toggle?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val on = !NpSettings.showJalons(activity)
            NpSettings.setShowJalons(activity, on)
            paintToggle()
            goo?.jelly()
            Toast.makeText(activity, if (on) R.string.np_jalons_shown else R.string.np_jalons_hidden, Toast.LENGTH_SHORT).show()
            onToggleJalons?.invoke()
        }

        val pill = nav.findViewById<View>(R.id.np_nav_pill) ?: return
        val label = nav.findViewById<TextView>(R.id.np_nav_label)
        val items = railTabs.map { nav.findViewById<ImageView>(itemId(it)) }

        fun paint(hover: Int) {
            items.forEachIndexed { i, icon ->
                val tab = railTabs[i]
                when {
                    i == hover -> {
                        icon?.setBackgroundResource(R.drawable.np_rail_item_hover)
                        icon?.setColorFilter(0xFFFFFFFF.toInt())
                    }

                    tab == shownTab -> {
                        icon?.setBackgroundResource(R.drawable.np_rail_item_active)
                        icon?.setColorFilter(0xFF1F1B19.toInt())
                    }

                    else -> {
                        icon?.background = null
                        icon?.setColorFilter(0xFF3A3330.toInt())
                    }
                }
            }
        }
        paint(-1)

        fun indexAt(x: Float, y: Float): Int {
            val horizontal = NpSettings.navHorizontal(activity)
            items.forEachIndexed { i, v ->
                if (v != null && (if (horizontal) x < v.right + 2 else y < v.bottom + 2)) return i
            }
            return items.lastIndex
        }

        fun showLabel(i: Int) {
            val icon = items[i] ?: return
            label ?: return
            label.text = railLabel(activity, railTabs[i])
            val pillLoc = IntArray(2).also { pill.getLocationInWindow(it) }
            val navLoc = IntArray(2).also { nav.getLocationInWindow(it) }
            if (label.visibility != View.VISIBLE) {
                label.alpha = 0f
                label.visibility = View.VISIBLE
                label.animate().alpha(1f).setDuration(120).start()
            }
            fun place() {
                if (NpSettings.navHorizontal(activity)) {
                    val centerX = pillLoc[0] - navLoc[0] + icon.left + icon.width / 2f
                    label.translationY = 0f
                    label.translationX = (centerX - label.width / 2f).coerceAtLeast(0f)
                } else {
                    val centerY = pillLoc[1] - navLoc[1] + icon.top + icon.height / 2f
                    label.translationX = 0f
                    label.translationY = centerY - label.height / 2f
                }
            }
            place()
            label.post { place() }
        }

        fun hideLabel() {
            label?.animate()?.alpha(0f)?.setDuration(160)?.withEndAction { label.visibility = View.INVISIBLE }?.start()
        }

        var hover = -1
        pill.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val i = indexAt(event.x, event.y)
                    if (i != hover) {
                        hover = i
                        // une seule vibration par entrée : en arrivant dessus, rien au relâcher
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        paint(i)
                        showLabel(i)
                    }
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val i = hover
                    hover = -1
                    hideLabel()
                    paint(-1)
                    if (i in railTabs.indices) {
                        val tab = railTabs[i]
                        if (tab == shownTab) {
                            onReselect?.invoke()
                        } else if (onSelect?.invoke(tab) != true) {
                            go(activity, tab)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    hover = -1
                    hideLabel()
                    paint(-1)
                    true
                }

                else -> false
            }
        }
    }

    /** Choix vertical / horizontal depuis le menu burger. */
    fun pickLayout(activity: Activity, onChanged: () -> Unit) {
        val labels = arrayOf(activity.getString(R.string.np_nav_vertical), activity.getString(R.string.np_nav_horizontal))
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(R.string.np_nav_style)
            .setSingleChoiceItems(labels, if (NpSettings.navHorizontal(activity)) 1 else 0) { dialog, which ->
                NpSettings.setNavHorizontal(activity, which == 1)
                dialog.dismiss()
                applyLayout(activity)
                onChanged()
            }
            .show()
    }

    fun layoutLabel(activity: Activity) = activity.getString(R.string.np_nav_style) + " : " +
        activity.getString(if (NpSettings.navHorizontal(activity)) R.string.np_nav_horizontal else R.string.np_nav_vertical)

    fun hide(activity: Activity) {
        activity.findViewById<View>(R.id.np_nav)?.visibility = View.GONE
    }

    fun comingSoon(context: Context) {
        Toast.makeText(context, R.string.np_coming_soon, Toast.LENGTH_SHORT).show()
    }

    private fun itemId(tab: String) = when (tab) {
        NP_TAB_GALLERY -> R.id.np_nav_gallery
        NP_TAB_JALONS -> R.id.np_nav_jalons
        NP_TAB_ALL -> R.id.np_nav_all
        else -> R.id.np_nav_folders
    }
}
