package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Jalons Aura : un jalon commence à une photo (son « point de coupe ») et regroupe toutes les
 * photos prises ensuite — y compris les jours suivants — jusqu'au jalon suivant.
 * Les photos plus anciennes que le tout premier jalon n'appartiennent à aucun jalon.
 * Les points de coupe sont stockés localement (préférences) sous forme de liste JSON.
 */
object NpJalons {
    /** Préfixe des en-têtes de jalon insérés dans la grille. */
    const val PREFIX = "✂"
    private const val SEP = "\u001F"
    private const val PREFS = "np_jalons"
    private const val KEY_CUTS = "cuts"

    data class Cut(val path: String, var name: String, val taken: Long)

    /** Ce qu'affiche un en-tête de jalon. */
    data class Info(val cutPath: String, val name: String, val range: String, val count: Int, val focused: Boolean)

    /** Positions (dans la liste affichée) couvertes par chaque jalon : en-tête → dernière photo. */
    var lastRanges: List<IntRange> = emptyList()
        private set

    private var cache: MutableList<Cut>? = null
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dayTimeFormat = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())

    fun all(context: Context): MutableList<Cut> {
        cache?.let { return it }
        val list = ArrayList<Cut>()
        try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CUTS, "[]") ?: "[]"
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(Cut(o.getString("path"), o.optString("name", ""), o.optLong("taken", 0L)))
            }
        } catch (ignored: Exception) {
        }
        cache = list
        return list
    }

    private fun save(context: Context) {
        val array = JSONArray()
        all(context).forEach {
            array.put(JSONObject().put("path", it.path).put("name", it.name).put("taken", it.taken))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CUTS, array.toString()).apply()
    }

    fun isCut(context: Context, path: String) = all(context).any { it.path == path }

    /** Ajoute un point de coupe ; renvoie false s'il existait déjà. */
    fun add(context: Context, medium: Medium): Boolean {
        if (isCut(context, medium.path)) return false
        all(context).add(Cut(medium.path, "", timeOf(medium)))
        save(context)
        return true
    }

    fun remove(context: Context, path: String) {
        all(context).removeAll { it.path == path }
        save(context)
    }

    fun rename(context: Context, path: String, name: String) {
        all(context).firstOrNull { it.path == path }?.name = name.trim()
        save(context)
    }

    fun timeOf(medium: Medium) = if (medium.taken > 0L) medium.taken else medium.modified

    fun timeLabel(ts: Long): String = timeFormat.format(ts)

    private fun sameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    fun rangeLabel(start: Long, end: Long): String {
        return if (sameDay(start, end)) {
            "${timeFormat.format(start)} → ${timeFormat.format(end)}"
        } else {
            "${dayTimeFormat.format(start)} → ${dayTimeFormat.format(end)}"
        }
    }

    /** Pour chaque photo, le jalon auquel elle appartient (le dernier point de coupe qui la précède). */
    fun groupsOf(context: Context, media: List<Medium>): Map<String, Cut> {
        val cuts = all(context).sortedBy { it.taken }
        if (cuts.isEmpty()) return emptyMap()
        val times = LongArray(cuts.size) { cuts[it].taken }
        val result = HashMap<String, Cut>(media.size)
        media.forEach { m ->
            val t = timeOf(m)
            var lo = 0
            var hi = times.size - 1
            var found = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (times[mid] <= t) {
                    found = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            if (found >= 0) result[m.path] = cuts[found]
        }
        return result
    }

    fun isJalonTitle(title: String) = title.startsWith(PREFIX)

    /** Identité stable d'un en-tête (pour les animations) : le chemin du point de coupe. */
    fun keyOf(title: String) = title.substringBefore(SEP)

    fun infoOf(title: String): Info? {
        if (!isJalonTitle(title)) return null
        val parts = title.removePrefix(PREFIX).split(SEP)
        if (parts.size < 5) return null
        return Info(parts[0], parts[3], parts[2], parts[1].toIntOrNull() ?: 0, parts[4] == "1")
    }

    /**
     * Insère un en-tête avant la première photo affichée de chaque jalon.
     * [focusCut] : n'afficher que les photos de ce jalon.
     */
    fun decorate(context: Context, items: List<ThumbnailItem>, focusCut: String?): ArrayList<ThumbnailItem> {
        var clean: List<ThumbnailItem> = items.filterNot { it is ThumbnailSection && isJalonTitle(it.title) }
        val groups = groupsOf(context, clean.filterIsInstance<Medium>())

        if (focusCut != null) {
            clean = clean.filter { it !is Medium || groups[it.path]?.path == focusCut }
            clean = dropEmptySections(clean)
        }

        if (groups.isEmpty()) {
            lastRanges = emptyList()
            return ArrayList(clean)
        }

        val members = HashMap<String, MutableList<Medium>>()
        clean.forEach { if (it is Medium) groups[it.path]?.let { cut -> members.getOrPut(cut.path) { ArrayList() }.add(it) } }

        val result = ArrayList<ThumbnailItem>(clean.size + 16)
        val ranges = ArrayList<IntRange>()
        var currentCut: String? = null
        var rangeStart = -1
        var rangeEnd = -1
        val seen = HashSet<String>()

        fun closeRange() {
            if (rangeStart >= 0 && rangeEnd >= rangeStart) ranges.add(rangeStart..rangeEnd)
            rangeStart = -1
            rangeEnd = -1
        }

        clean.forEach { item ->
            if (item is Medium) {
                val cut = groups[item.path]
                if (cut?.path != currentCut) {
                    closeRange()
                    currentCut = cut?.path
                }

                if (cut != null && seen.add(cut.path)) {
                    val list = members[cut.path].orEmpty()
                    val times = list.map { timeOf(it) }
                    val range = rangeLabel(times.minOrNull() ?: cut.taken, times.maxOrNull() ?: cut.taken)
                    val title = PREFIX + cut.path + SEP + list.size + SEP + range + SEP + cut.name + SEP + (if (focusCut != null) "1" else "0")
                    rangeStart = result.size
                    result.add(ThumbnailSection(title))
                }
                result.add(item)
                if (cut != null) rangeEnd = result.size - 1
            } else {
                result.add(item)
            }
        }
        closeRange()
        lastRanges = ranges
        return result
    }

    fun dropEmptySections(items: List<ThumbnailItem>): List<ThumbnailItem> {
        val result = ArrayList<ThumbnailItem>(items.size)
        items.forEachIndexed { index, item ->
            if (item is ThumbnailSection && !isJalonTitle(item.title)) {
                val next = items.getOrNull(index + 1)
                if (next == null || (next is ThumbnailSection && !isJalonTitle(next.title))) return@forEachIndexed
            }
            result.add(item)
        }
        return result
    }

    /** Positions de la bande de miniatures où un jalon commence. */
    fun gapsBefore(context: Context, media: List<Medium>): Set<Int> {
        val groups = groupsOf(context, media)
        if (groups.isEmpty()) return emptySet()
        val gaps = HashSet<Int>()
        for (i in 1 until media.size) {
            if (groups[media[i - 1].path]?.path != groups[media[i].path]?.path) gaps.add(i)
        }
        return gaps
    }
}
