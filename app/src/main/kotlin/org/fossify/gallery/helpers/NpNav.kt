package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
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
            NP_TAB_ALL -> Intent(context, MediaActivity::class.java).apply {
                putExtra(DIRECTORY, "")
                putExtra(NP_FORCE_SHOW_ALL, true)
            }

            NP_TAB_VIDEOS -> Intent(context, MediaActivity::class.java).apply {
                putExtra(DIRECTORY, cameraPath(context))
                putExtra(NP_ONLY_VIDEOS, true)
                if (cameraPath(context).isEmpty()) putExtra(NP_FORCE_SHOW_ALL, true)
            }

            else -> Intent(context, MediaActivity::class.java).apply {
                putExtra(DIRECTORY, cameraPath(context))
                if (cameraPath(context).isEmpty()) putExtra(NP_FORCE_SHOW_ALL, true)
            }
        }
        intent.putExtra(NP_TAB, tab)
        intent.putExtra(SKIP_AUTHENTICATION, true)
        return intent
    }

    /** Change d'onglet : une seule activité dans la pile, avec un fondu. */
    fun go(activity: Activity, tab: String) {
        val intent = intentFor(activity, tab).apply {
            putExtra(NP_CASCADE, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        activity.startActivity(intent)
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    /** Branche la barre flottante incluse dans la mise en page de l'activité. */
    fun setup(
        activity: Activity,
        current: String,
        onReselect: (() -> Unit)? = null,
        onSelect: ((String) -> Boolean)? = null,
        onToggleJalons: (() -> Unit)? = null
    ) {
        val nav = activity.findViewById<View>(R.id.np_nav) ?: return
        val toggle = nav.findViewById<ImageView>(R.id.np_nav_toggle)
        fun paintToggle() {
            val on = NpSettings.showJalons(activity)
            toggle?.setBackgroundResource(if (on) R.drawable.np_round_active_bg else R.drawable.np_round_bg)
            toggle?.imageAlpha = if (on) 255 else 120
        }
        paintToggle()
        toggle?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val on = !NpSettings.showJalons(activity)
            NpSettings.setShowJalons(activity, on)
            paintToggle()
            Toast.makeText(activity, if (on) R.string.np_jalons_shown else R.string.np_jalons_hidden, Toast.LENGTH_SHORT).show()
            onToggleJalons?.invoke()
        }
        tabs.forEach { tab ->
            val item = nav.findViewById<View>(itemId(tab)) ?: return@forEach
            val icon = nav.findViewById<ImageView>(iconId(tab))
            val label = nav.findViewById<TextView>(labelId(tab))
            val active = tab == current
            icon?.setBackgroundResource(if (active) R.drawable.np_nav_active_bg else 0)
            label?.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            label?.setTextColor(if (active) 0xFF1F1B19.toInt() else 0xFF3A3330.toInt())
            item.isSelected = active
            item.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (active) {
                    onReselect?.invoke()
                } else if (onSelect?.invoke(tab) != true) {
                    go(activity, tab)
                }
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
        NP_TAB_VIDEOS -> R.id.np_nav_videos
        NP_TAB_JALONS -> R.id.np_nav_jalons
        NP_TAB_ALL -> R.id.np_nav_all
        else -> R.id.np_nav_folders
    }

    private fun iconId(tab: String) = when (tab) {
        NP_TAB_GALLERY -> R.id.np_nav_gallery_icon
        NP_TAB_VIDEOS -> R.id.np_nav_videos_icon
        NP_TAB_JALONS -> R.id.np_nav_jalons_icon
        NP_TAB_ALL -> R.id.np_nav_all_icon
        else -> R.id.np_nav_folders_icon
    }

    private fun labelId(tab: String) = when (tab) {
        NP_TAB_GALLERY -> R.id.np_nav_gallery_label
        NP_TAB_VIDEOS -> R.id.np_nav_videos_label
        NP_TAB_JALONS -> R.id.np_nav_jalons_label
        NP_TAB_ALL -> R.id.np_nav_all_label
        else -> R.id.np_nav_folders_label
    }
}
