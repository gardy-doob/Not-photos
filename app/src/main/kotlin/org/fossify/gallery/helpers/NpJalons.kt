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
 * Souvenirs Aurora : un souvenir commence à une photo (double tap) et regroupe toutes les photos
 * prises ensuite, y compris les jours suivants, jusqu'au souvenir suivant. Aucune photo ne reste
 * seule : celles d'avant le tout premier souvenir sont rangées dans « À classer ».
 * Les souvenirs sont stockés localement (préférences) sous forme de liste JSON.
 */
object NpJalons {
    /** Préfixe des en-têtes de souvenir insérés dans la grille. */
    const val PREFIX = "✂"
    private const val SEP = "\u001F"
    private const val PREFS = "np_souvenirs"
    private const val KEY = "souvenirs"

    data class Cut(val path: String, val endPath: String, var name: String, val taken: Long, val endTaken: Long)

    /** Groupe des photos plus anciennes que le premier souvenir. */
    const val ORPHAN = "__a_classer__"

    /** Ce qu'affiche un en-tête de souvenir. */
    data class Info(val cutPath: String, val name: String, val range: String, val count: Int, val focused: Boolean)

    /** Positions (dans la liste affichée) couvertes par chaque souvenir : en-tête → dernière photo. */
    var lastRanges: List<IntRange> = emptyList()
        private set

    /**
     * Mise en page de la liste affichée, case par case :
     * -1 = titre (date ou souvenir), 0..5 = colonne d'une photo libre (6 colonnes),
     * 10..12 = colonne d'une photo de souvenir (3 colonnes).
     */
    var lastLayout: IntArray = IntArray(0)
        private set

    /** Photo de début choisie, en attente de la photo de fin (partagée entre Galerie et visionneuse). */
    var pendingStart: Medium? = null

    private var cache: MutableList<Cut>? = null
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dayTimeFormat = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())

    fun all(context: Context): MutableList<Cut> {
        cache?.let { return it }
        val list = ArrayList<Cut>()
        try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(Cut(o.getString("path"), o.optString("end", ""), o.optString("name", ""), o.getLong("taken"), o.optLong("endTaken", Long.MAX_VALUE)))
            }
        } catch (ignored: Exception) {
        }
        cache = list
        return list
    }

    private fun save(context: Context) {
        val array = JSONArray()
        all(context).forEach {
            array.put(
                JSONObject().put("path", it.path).put("end", it.endPath).put("name", it.name)
                    .put("taken", it.taken).put("endTaken", it.endTaken)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    fun timeOf(medium: Medium) = if (medium.taken > 0L) medium.taken else medium.modified

    fun timeLabel(ts: Long): String = timeFormat.format(ts)

    fun cutOf(context: Context, medium: Medium): Cut? {
        val t = timeOf(medium)
        return all(context).filter { it.taken <= t }.maxByOrNull { it.taken }
    }

    /** Double tap : un nouveau souvenir commence à cette photo. */
    fun addStart(context: Context, medium: Medium): Boolean {
        if (all(context).any { it.path == medium.path }) return false
        all(context).add(Cut(medium.path, "", "", timeOf(medium), Long.MAX_VALUE))
        all(context).sortBy { it.taken }
        save(context)
        return true
    }

    enum class AddResult { OK, OVERLAP, SAME }

    /** Crée un souvenir entre deux photos (dans n'importe quel ordre). */
    fun add(context: Context, a: Medium, b: Medium): AddResult {
        if (a.path == b.path) return AddResult.SAME
        val (first, last) = if (timeOf(a) <= timeOf(b)) a to b else b to a
        val start = timeOf(first)
        val end = timeOf(last)
        if (all(context).any { !(end < it.taken || start > it.endTaken) }) return AddResult.OVERLAP
        all(context).add(Cut(first.path, last.path, "", start, end))
        all(context).sortBy { it.taken }
        save(context)
        return AddResult.OK
    }

    fun remove(context: Context, path: String) {
        all(context).removeAll { it.path == path }
        save(context)
    }

    fun rename(context: Context, path: String, name: String) {
        all(context).firstOrNull { it.path == path }?.name = name.trim()
        save(context)
    }

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

    /** Pour chaque photo, le souvenir auquel elle appartient. */
    fun groupsOf(context: Context, media: List<Medium>): Map<String, Cut> {
        // l'heure d'une photo peut changer légèrement entre le cache et le scan (EXIF / MediaStore) :
        // la photo de début reprend toujours l'heure qu'elle a dans la liste affichée
        val timeByPath = HashMap<String, Long>(media.size)
        media.forEach { timeByPath[it.path] = timeOf(it) }
        val cuts = all(context).map { c -> timeByPath[c.path]?.let { if (it != c.taken) c.copy(taken = it) else c } ?: c }
            .sortedBy { it.taken }
        if (cuts.isEmpty()) return emptyMap()
        val times = LongArray(cuts.size) { cuts[it].taken }
        val ownIndex = HashMap<String, Int>(cuts.size)
        cuts.forEachIndexed { i, cut -> ownIndex[cut.path] = i }
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
            // la photo de début ouvre toujours son propre souvenir (même à heure égale, en rafale)
            ownIndex[m.path]?.let { found = it }
            if (found >= 0) result[m.path] = cuts[found]
        }
        return result
    }

    private val cardDateFormat = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())

    /**
     * Mise en page « cartes » : chaque souvenir est une carte (avec sa date au-dessus) ;
     * les photos d'avant le premier souvenir forment la carte « À classer ».
     */
    fun decorateCards(context: Context, items: List<ThumbnailItem>, focusCut: String?, orphanTitle: String): ArrayList<ThumbnailItem> {
        val media = items.filterIsInstance<Medium>()
        val groups = groupsOf(context, media)
        val keyOf = { m: Medium -> groups[m.path]?.path ?: ORPHAN }
        val visible = if (focusCut != null) media.filter { keyOf(it) == focusCut } else media

        val members = LinkedHashMap<String, MutableList<Medium>>()
        visible.forEach { members.getOrPut(keyOf(it)) { ArrayList() }.add(it) }

        val result = ArrayList<ThumbnailItem>(visible.size + members.size * 2)
        val ranges = ArrayList<IntRange>()
        var dateIndex = 0
        var current: String? = null
        visible.forEach { m ->
            val key = keyOf(m)
            if (key != current) {
                if (current != null) ranges.add(ranges.removeAt(ranges.lastIndex).first..(result.size - 1))
                current = key
                val list = members[key].orEmpty()
                val times = list.map { timeOf(it) }
                val newest = times.maxOrNull() ?: 0L
                val oldest = times.minOrNull() ?: 0L
                val dateLabel = if (sameDay(newest, oldest)) {
                    cardDateFormat.format(newest)
                } else {
                    "${cardDateFormat.format(oldest)} \u2192 ${cardDateFormat.format(newest)}"
                }
                result.add(ThumbnailSection(dateLabel.replaceFirstChar { it.uppercase() } + "\u200B".repeat(dateIndex++)))
                val cut = groups[m.path]
                val name = if (cut == null) orphanTitle else cut.name
                val title = PREFIX + key + SEP + list.size + SEP + rangeLabel(oldest, newest) + SEP + name + SEP + (if (focusCut != null) "1" else "0")
                ranges.add(result.size..result.size)
                result.add(ThumbnailSection(title))
            }
            result.add(m)
        }
        if (current != null) ranges.add(ranges.removeAt(ranges.lastIndex).first..(result.size - 1))
        lastRanges = ranges
        lastLayout = layoutOf(result, ranges)
        return result
    }

    fun isJalonTitle(title: String) = title.startsWith(PREFIX)

    /** Identité stable d'un en-tête (pour les animations) : la photo de début. */
    fun keyOf(title: String) = title.substringBefore(SEP)

    fun infoOf(title: String): Info? {
        if (!isJalonTitle(title)) return null
        val parts = title.removePrefix(PREFIX).split(SEP)
        if (parts.size < 5) return null
        return Info(parts[0], parts[3], parts[2], parts[1].toIntOrNull() ?: 0, parts[4] == "1")
    }

    /**
     * Insère un en-tête avant la première photo affichée de chaque souvenir.
     * [focusCut] : n'afficher que les photos de ce souvenir.
     */
    fun decorate(context: Context, items: List<ThumbnailItem>, focusCut: String?): ArrayList<ThumbnailItem> {
        var clean: List<ThumbnailItem> = items.filterNot { it is ThumbnailSection && isJalonTitle(it.title) }
        val groups = groupsOf(context, clean.filterIsInstance<Medium>())

        if (focusCut != null) {
            clean = clean.filter { it !is Medium || groups[it.path]?.path == focusCut }
            clean = dropEmptySections(clean)
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
                    val range = rangeLabel(times.minOrNull() ?: cut.taken, times.maxOrNull() ?: cut.endTaken)
                    val title = PREFIX + cut.path + SEP + list.size + SEP + range + SEP + cut.name + SEP + (if (focusCut != null) "1" else "0")
                    rangeStart = result.size
                    result.add(ThumbnailSection(title))
                }
                result.add(item)
                if (cut != null) rangeEnd = result.size - 1
            } else {
                // un titre de date au milieu d'un souvenir ne le coupe pas
                result.add(item)
            }
        }
        closeRange()
        lastRanges = ranges
        lastLayout = layoutOf(result, ranges)
        return result
    }

    private fun layoutOf(items: List<ThumbnailItem>, ranges: List<IntRange>): IntArray {
        val layout = IntArray(items.size)
        var freeCol = 0
        var cardCol = 0
        var r = 0
        items.forEachIndexed { i, item ->
            while (r < ranges.size && i > ranges[r].last) r++
            val inCard = r < ranges.size && i in ranges[r]
            if (item is ThumbnailSection) {
                layout[i] = -1
                freeCol = 0
                cardCol = 0
            } else if (inCard) {
                layout[i] = 10 + cardCol
                cardCol = (cardCol + 1) % 3
                freeCol = 0
            } else {
                layout[i] = freeCol
                freeCol = (freeCol + 1) % 6
                cardCol = 0
            }
        }
        return layout
    }

    /** Liste sans souvenirs mais avec la même mise en page de photos libres. */
    fun plain(items: List<ThumbnailItem>): ArrayList<ThumbnailItem> {
        val result = ArrayList(items.filterNot { it is ThumbnailSection && isJalonTitle(it.title) })
        lastRanges = emptyList()
        lastLayout = layoutOf(result, emptyList())
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

    /** Positions de la bande de miniatures où un souvenir commence ou se termine. */
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
