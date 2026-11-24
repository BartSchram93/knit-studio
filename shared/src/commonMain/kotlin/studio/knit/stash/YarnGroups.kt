package studio.knit.stash

import studio.knit.model.Yarn

/** A folder of yarns of one brand. [brand] is empty for yarns without a brand. */
data class YarnGroup(val brand: String, val yarns: List<Yarn>) {
    val key: String get() = brandKey(brand)
    /** Skeins owned in this folder; null when no yarn has a count. */
    val skeins: Double? get() = yarns.mapNotNull { it.skeins }.takeIf { it.isNotEmpty() }?.sum()
}

private val yarnOrder = compareBy<Yarn>({ it.name.trim().lowercase() }, { it.color.trim().lowercase() }, { it.label.trim().lowercase() })

/**
 * Groups yarns by brand like [groupByBrand] does for needles: brands alphabetically (case does not
 * matter, the most used spelling is shown), yarns by name and colour within each, no brand last.
 */
fun groupYarnsByBrand(yarns: List<Yarn>): List<YarnGroup> {
    val groups = yarns.groupBy { brandKey(it.brand) }
    val named = groups.filterKeys { it.isNotEmpty() }.map { (_, list) ->
        val spelling = list.map { it.brand.trim() }.groupingBy { it }.eachCount()
            .maxWith(compareBy({ it.value }, { e -> e.key.count { c -> c.isUpperCase() } })).key
        YarnGroup(spelling, list.sortedWith(yarnOrder))
    }.sortedBy { it.key }
    val unnamed = groups[""].orEmpty()
    return if (unnamed.isEmpty()) named else named + YarnGroup("", unnamed.sortedWith(yarnOrder))
}

/** Brands of yarn in the stash, for quick picking. */
fun knownYarnBrands(yarns: List<Yarn>): List<String> = groupYarnsByBrand(yarns).map { it.brand }.filter { it.isNotEmpty() }

/**
 * A stash yarn as added to a pattern: a copy with its own id and without the stash's skein count,
 * which is what she owns, not what the pattern needs.
 */
fun yarnForPattern(stash: Yarn, newId: String): Yarn = stash.copy(id = newId, photoId = null, skeins = null)

/** Skeins of [yarn] (same brand, name and colour) that are in the stash. */
fun ownedSkeins(yarn: Yarn, stash: List<Yarn>): Double? {
    fun k(s: String) = s.trim().lowercase()
    val same = stash.filter { brandKey(it.brand) == brandKey(yarn.brand) && k(it.name) == k(yarn.name) && k(it.color) == k(yarn.color) }
    return same.mapNotNull { it.skeins }.takeIf { it.isNotEmpty() }?.sum()
}
