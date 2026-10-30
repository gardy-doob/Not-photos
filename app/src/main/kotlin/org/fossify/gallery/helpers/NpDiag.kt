package org.fossify.gallery.helpers

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import android.widget.Toast
import androidx.core.content.FileProvider
import org.fossify.gallery.BuildConfig
import org.fossify.gallery.extensions.config
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic Aurora : garde en mémoire les derniers événements (chargements, onglets, plantages)
 * et mesure les saccades d'affichage. « Envoyer un diagnostic » (menu burger) produit un fichier
 * texte à partager.
 */
object NpDiag {
    private const val MAX_EVENTS = 600
    private val events = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var lastMark = 0L

    // saccades par écran : images > 33 ms (perceptible) et > 100 ms (blocage)
    private val frames = HashMap<String, IntArray>()
    private val worst = HashMap<String, Long>()
    private var metricsThread: HandlerThread? = null

    @Synchronized
    fun mark(event: String) {
        val now = SystemClock.elapsedRealtime()
        val delta = if (lastMark == 0L) 0 else now - lastMark
        lastMark = now
        events.addLast("${timeFormat.format(Date())}  +${delta}ms  $event")
        while (events.size > MAX_EVENTS) events.removeFirst()
    }

    /** Mesure les images trop lentes de cette fenêtre. */
    fun watch(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val name = activity.javaClass.simpleName
        mark("ouvre $name")
        val thread = metricsThread ?: HandlerThread("aurora-frames").also { it.start(); metricsThread = it }
        try {
            activity.window.addOnFrameMetricsAvailableListener(Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                val ms = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000
                synchronized(this) {
                    val counts = frames.getOrPut(name) { IntArray(3) }
                    counts[0]++
                    if (ms > 33) counts[1]++
                    if (ms > 100) {
                        counts[2]++
                        if (ms > 250) mark("blocage ${ms}ms dans $name")
                    }
                    if (ms > (worst[name] ?: 0L)) worst[name] = ms
                }
            }, Handler(thread.looper))
        } catch (ignored: Exception) {
        }
    }

    @Synchronized
    private fun snapshot(): Pair<List<String>, Map<String, String>> {
        val jank = frames.mapValues { (k, v) ->
            val pct = if (v[0] > 0) 100.0 * v[1] / v[0] else 0.0
            String.format(Locale.US, "%d images, %d lentes (%.1f%%), %d blocages, pire %d ms", v[0], v[1], pct, v[2], worst[k] ?: 0)
        }
        return events.toList() to jank
    }

    fun buildReport(context: Context): String {
        val (log, jank) = snapshot()
        val sb = StringBuilder()
        val rt = Runtime.getRuntime()
        sb.appendLine("=== Diagnostic Aurora ===")
        sb.appendLine("Date : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("Version : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
        sb.appendLine("Appareil : ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Mémoire : ${(rt.totalMemory() - rt.freeMemory()) / 1_048_576} Mo utilisés / ${rt.maxMemory() / 1_048_576} Mo max")
        sb.appendLine()
        sb.appendLine("--- Réglages ---")
        val c = context.config
        sb.appendLine("Souvenirs affichés : ${NpSettings.showJalons(context)} · style : ${if (NpSettings.style(context) == NpSettings.STYLE_CARDS) "cartes" else "frise"}")
        sb.appendLine("Souvenirs : ${NpJalons.all(context).size}")
        sb.appendLine("Dossiers Galerie : ${NpGallery.folders(context)}")
        sb.appendLine("Dossiers exclus : ${c.excludedFolders.size} · cachés visibles : ${c.shouldShowHidden}")
        sb.appendLine("Colonnes : ${c.mediaColumnCnt} · tri : ${c.sorting} · regroupement : ${c.groupBy}")
        sb.appendLine()
        sb.appendLine("--- Saccades par écran ---")
        if (jank.isEmpty()) sb.appendLine("(aucune mesure)")
        jank.forEach { (k, v) -> sb.appendLine("$k : $v") }
        sb.appendLine()
        sb.appendLine("--- Derniers plantages ---")
        val history = File(context.filesDir, NpCrash.HISTORY)
        sb.appendLine(if (history.exists()) history.readText().takeLast(12000) else "(aucun)")
        sb.appendLine()
        sb.appendLine("--- Événements ---")
        log.forEach { sb.appendLine(it) }
        sb.appendLine()
        sb.appendLine("--- Journal système de l'app ---")
        try {
            val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", "1500", "--pid=${android.os.Process.myPid()}"))
            sb.append(p.inputStream.bufferedReader().readText().takeLast(200_000))
        } catch (e: Exception) {
            sb.appendLine("(indisponible : ${e.message})")
        }
        return sb.toString()
    }

    /** Écrit le fichier et ouvre le partage Android (mail, WhatsApp, Drive…). */
    fun share(activity: Activity) {
        Toast.makeText(activity, "Préparation du diagnostic…", Toast.LENGTH_SHORT).show()
        org.fossify.commons.helpers.ensureBackgroundThread {
            try {
                val dir = File(activity.cacheDir, "diagnostic").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = File(dir, "aurora-diagnostic-$stamp.txt")
                file.writeText(buildReport(activity))
                val uri = FileProvider.getUriForFile(activity, "${BuildConfig.APPLICATION_ID}.provider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Diagnostic Aurora $stamp")
                    clipData = ClipData.newRawUri("diagnostic", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.runOnUiThread {
                    activity.startActivity(Intent.createChooser(send, "Envoyer le diagnostic"))
                }
            } catch (e: Exception) {
                activity.runOnUiThread { Toast.makeText(activity, "Diagnostic impossible : ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }
}
