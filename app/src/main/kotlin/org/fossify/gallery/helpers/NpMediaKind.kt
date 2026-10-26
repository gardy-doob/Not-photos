package org.fossify.gallery.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Type de vidéo, pour afficher le bon picto dans la grille, comme Google Photos :
 *  - vidéo normale : durée + rond « lecture »
 *  - vidéo cinématique (Pixel) : durée + crochets avec flèche ; repérée par la balise
 *    « SpecialType-CINEMATIC » dans l'en-tête MP4 (boîte moov)
 *  - photo cinématique (créée par Google Photos) : étincelles, sans durée ; repérée par
 *    « CINEMATIC » dans le nom du fichier
 */
object NpMediaKind {
    const val VIDEO = 0
    const val CINEMATIC_VIDEO = 1
    const val CINEMATIC_PHOTO = 2

    private const val PREFS = "np_media_kind"
    private val cache = ConcurrentHashMap<String, Int>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var refreshScheduled = false
    private var loaded = false

    /** Renvoie le type connu, ou VIDEO en attendant l'analyse (onReady est alors appelé plus tard). */
    fun get(context: Context, path: String, onReady: () -> Unit): Int {
        val name = path.substringAfterLast('/')
        if (name.contains("CINEMATIC", ignoreCase = true)) return CINEMATIC_PHOTO

        ensureLoaded(context)
        cache[path]?.let { return it }

        if (pending.add(path)) {
            val appContext = context.applicationContext
            executor.execute {
                val kind = if (hasCinematicTag(path)) CINEMATIC_VIDEO else VIDEO
                cache[path] = kind
                pending.remove(path)
                appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(path, kind).apply()
                if (kind != VIDEO) scheduleRefresh(onReady)
            }
        }
        return VIDEO
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            if (v is Int) cache[k] = v
        }
    }

    private fun scheduleRefresh(onReady: () -> Unit) {
        mainHandler.post {
            if (refreshScheduled) return@post
            refreshScheduled = true
            mainHandler.postDelayed({
                refreshScheduled = false
                onReady()
            }, 400)
        }
    }

    private val marker = "SpecialType-CINEMATIC".toByteArray(Charsets.US_ASCII)

    /** Parcourt les boîtes MP4 de premier niveau et cherche la balise dans « moov ». */
    private fun hasCinematicTag(path: String): Boolean {
        return try {
            val file = File(path)
            if (!file.isFile) return false
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                var offset = 0L
                var guard = 0
                while (offset + 8 <= length && guard++ < 64) {
                    raf.seek(offset)
                    var size = raf.readInt().toLong() and 0xFFFFFFFFL
                    val type = ByteArray(4).also { raf.readFully(it) }
                    var header = 8L
                    if (size == 1L) {
                        size = raf.readLong()
                        header = 16L
                    } else if (size == 0L) {
                        size = length - offset
                    }
                    if (size < header) return false
                    if (String(type, Charsets.US_ASCII) == "moov") {
                        val toRead = (size - header).coerceAtMost(4L * 1024 * 1024).toInt()
                        val data = ByteArray(toRead)
                        raf.seek(offset + header)
                        raf.readFully(data)
                        return indexOf(data, marker) >= 0
                    }
                    offset += size
                }
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray): Int {
        outer@ for (i in 0..data.size - pattern.size) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
