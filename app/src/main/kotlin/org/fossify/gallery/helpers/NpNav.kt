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

    /**
     * Barre verticale sous le pouce : des icônes seules ; en posant le doigt, le nom de la vue
     * apparaît à gauche et on peut glisser pour en choisir une autre, puis relâcher.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun setup(
        activity: Activity,
        current: String,
        onReselect: (() -> Unit)? = null,
        onSelect: ((String) -> Boolean)? = null,
        onToggleJalons: (() -> Unit)? = null
    ) {
        val nav = activity.findViewById<View>(R.id.np_nav) ?: return
        val shownTab = if (current == NP_TAB_VIDEOS) NP_TAB_GALLERY else current

        // bouton Souvenirs : en négatif quand le mode est actif
        val toggle = nav.findViewById<ImageView>(R.id.np_nav_toggle)
        fun paintToggle() {
            val on = NpSettings.showJalons(activity)
            toggle?.setBackgroundResource(if (on) R.drawable.np_round_negative_bg else R.drawable.np_round_bg)
            toggle?.setColorFilter(if (on) 0xFFFFFFFF.toInt() else 0xFF3A3330.toInt())
        }
        paintToggle()
        toggle?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val on = !NpSettings.showJalons(activity)
            NpSettings.setShowJalons(activity, on)
            paintToggle()
            it.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }.start()
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

        fun indexAt(y: Float): Int {
            items.forEachIndexed { i, v -> if (v != null && y < v.bottom + 2) return i }
            return items.lastIndex
        }

        fun showLabel(i: Int) {
            val icon = items[i] ?: return
            label ?: return
            label.text = railLabel(activity, railTabs[i])
            val pillLoc = IntArray(2).also { pill.getLocationInWindow(it) }
            val navLoc = IntArray(2).also { nav.getLocationInWindow(it) }
            val centerY = pillLoc[1] - navLoc[1] + icon.top + icon.height / 2f
            if (label.visibility != View.VISIBLE) {
                label.alpha = 0f
                label.translationX = 12f * activity.resources.displayMetrics.density
                label.visibility = View.VISIBLE
                label.animate().alpha(1f).translationX(0f).setDuration(120).start()
            }
            label.post { label.translationY = centerY - label.height / 2f }
            label.translationY = centerY - (if (label.height > 0) label.height else 0) / 2f
        }

        fun hideLabel() {
            label?.animate()?.alpha(0f)?.setDuration(160)?.withEndAction { label.visibility = View.INVISIBLE }?.start()
        }

        var hover = -1
        pill.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val i = indexAt(event.y)
                    if (i != hover) {
                        hover = i
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
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
