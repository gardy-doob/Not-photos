package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Medium

/**
 * Ce que montre chaque onglet :
 *  - Galerie : l'appareil photo (DCIM/Camera) + les dossiers ajoutés dans les réglages
 *  - Vidéos : les vidéos de la Galerie
 *  - Toutes : tous les dossiers visibles
 */
object NpGallery {
    fun folders(context: Context): List<String> {
        val result = ArrayList<String>()
        NpNav.cameraPath(context).takeIf { it.isNotEmpty() }?.let { result.add(it.trimEnd('/')) }
        context.config.includedFolders.forEach { result.add(it.trimEnd('/')) }
        return result
    }

    fun isGalleryTab(tab: String) = tab == NP_TAB_GALLERY || tab == NP_TAB_VIDEOS || tab == NP_TAB_ALL

    fun filter(context: Context, tab: String, media: List<Medium>): List<Medium> {
        if (tab != NP_TAB_GALLERY && tab != NP_TAB_VIDEOS) return media
        val folders = folders(context)
        return media.filter { m ->
            val parent = m.parentPath.trimEnd('/')
            val inside = folders.any { parent.equals(it, true) || parent.startsWith("$it/", true) }
            inside && (tab != NP_TAB_VIDEOS || m.isVideo())
        }
    }
}
