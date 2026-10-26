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
 * Jalons Not Photos : un jalon commence à une photo (son « point de coupe ») et regroupe
 * toutes les photos prises ensuite le même jour, jusqu'au jalon suivant.
 * Les points de coupe sont stockés localement (préférences) sous forme de liste JSON.
 */
object NpJalons {
    const val PREFIX = "✂ "
    /** En-tête discret qui referme un jalon quand des photos hors jalon suivent. */
    const val END_PREFIX = "\u2003"
    private const val PREFS = "np_jalons"
    private const val KEY_CUTS = "cuts"

    data class Cut(val path: String, var name: String, val taken: Long)

    class Group(val cut: Cut, val number: Int, val members: MutableList<Medium> = ArrayList())

    /** Titre de section affiché → chemin du point de coupe (pour retrouver le jalon touché). */
    val sectionToCut = HashMap<String, String>()

    private var cache: MutableList<Cut>? = null
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

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

    private fun dayKey(ts: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    /** Pour chaque photo, le jalon auquel elle appartient (ou rien). */
    fun groupsOf(context: Context, media: List<Medium>): Map<String, Group> {
        val cuts = all(context).associateBy { it.path }
        if (cuts.isEmpty()) return emptyMap()
        val result = HashMap<String, Group>()
        media.groupBy { dayKey(timeOf(it)) }.values.forEach { day ->
            var current: Group? = null
            var number = 0
            day.sortedWith(compareBy<Medium> { timeOf(it) }.thenBy { it.path }).forEach { m ->
                val cut = cuts[m.path]
                if (cut != null) {
                    number++
                    current = Group(cut, number)
                }
                current?.let {
                    it.members.add(m)
                    result[m.path] = it
                }
            }
        }
        return result
    }

    fun title(group: Group): String {
        val name = group.cut.name.ifEmpty { "Jalon ${group.number}" }
        val times = group.members.map { timeOf(it) }
        val start = timeLabel(times.minOrNull() ?: 0L)
        val end = timeLabel(times.maxOrNull() ?: 0L)
        val count = group.members.size
        return "$PREFIX$name · $start → $end · $count ${if (count > 1) "éléments" else "élément"}"
    }

    /** Insère un en-tête de jalon avant la première photo (dans l'ordre affiché) de chaque jalon. */
    fun decorate(context: Context, items: ArrayList<ThumbnailItem>): ArrayList<ThumbnailItem> {
        val clean = items.filterNot { it is ThumbnailSection && (it.title.startsWith(PREFIX) || it.title.startsWith(END_PREFIX)) }
        val media = clean.filterIsInstance<Medium>()
        val groups = groupsOf(context, media)
        if (groups.isEmpty()) return ArrayList(clean)

        sectionToCut.clear()
        val seen = HashSet<Group>()
        val result = ArrayList<ThumbnailItem>(clean.size + 8)
        var previous: Group? = null
        var endCount = 0
        clean.forEach { item ->
            if (item is Medium) {
                val group = groups[item.path]
                if (group != null && seen.add(group)) {
                    val title = title(group)
                    sectionToCut[title] = group.cut.path
                    result.add(ThumbnailSection(title))
                } else if (group == null && previous != null) {
                    endCount++
                    result.add(ThumbnailSection(END_PREFIX + "Hors jalon" + "\u200B".repeat(endCount)))
                }
                previous = group
            } else {
                previous = null
            }
            result.add(item)
        }
        return result
    }

    /** Positions de la bande de miniatures où un jalon commence (ou se termine). */
    fun gapsBefore(context: Context, media: List<Medium>): Set<Int> {
        val groups = groupsOf(context, media)
        if (groups.isEmpty()) return emptySet()
        val gaps = HashSet<Int>()
        for (i in 1 until media.size) {
            val a = groups[media[i - 1].path]
            val b = groups[media[i].path]
            if (a !== b) gaps.add(i)
        }
        return gaps
    }
}
