package org.fossify.gallery.helpers

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.fossify.gallery.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Garde l'erreur d'un plantage et l'affiche au lancement suivant (bouton Copier). */
object NpCrash {
    private const val FILE = "aura_last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val text = "Aura ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} · ${Build.MODEL}\n" +
                    "Thread : ${thread.name}\n\n" + sw.toString()
                File(appContext.filesDir, FILE).writeText(text.take(20000))
            } catch (ignored: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun showIfAny(activity: Activity) {
        val file = File(activity.filesDir, FILE)
        if (!file.exists()) return
        val text = try { file.readText() } catch (e: Exception) { "" }
        file.delete()
        if (text.isEmpty()) return
        AlertDialog.Builder(activity)
            .setTitle("Aura a planté la dernière fois")
            .setMessage(text.take(3000))
            .setPositiveButton("Copier") { _, _ ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Aura crash", text))
                Toast.makeText(activity, "Erreur copiée", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Fermer", null)
            .show()
    }
}
