package org.fossify.gallery.activities

import android.content.Intent
import org.fossify.commons.activities.BaseSplashActivity
import org.fossify.commons.extensions.hasAllPermissions
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.getFavoriteFromPath
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.models.Favorite

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        // check if previously selected favorite items have been properly migrated into the new Favorites table
        if (config.wereFavoritesMigrated) {
            launchActivity()
        } else {
            if (config.appRunCount == 0) {
                config.wereFavoritesMigrated = true
                launchActivity()
            } else {
                config.wereFavoritesMigrated = true
                ensureBackgroundThread {
                    val favorites = ArrayList<Favorite>()
                    val favoritePaths = mediaDB.getFavorites().map { it.path }.toMutableList() as ArrayList<String>
                    favoritePaths.forEach {
                        favorites.add(getFavoriteFromPath(it))
                    }
                    favoritesDB.insertAll(favorites)

                    runOnUiThread {
                        launchActivity()
                    }
                }
            }
        }
    }

    private fun launchActivity() {
        // Aurora : droit sur la Galerie (une seule étape au lieu de Dossiers → Galerie) quand les droits sont déjà accordés
        val direct = config.defaultFolder.isEmpty() &&
            hasAllPermissions(org.fossify.gallery.helpers.getPermissionsToRequest())
        val intent = if (direct) {
            org.fossify.gallery.helpers.NpNav.intentFor(this, org.fossify.gallery.helpers.NP_TAB_GALLERY)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        finish()
    }
}
