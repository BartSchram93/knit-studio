package studio.knit.stash

import studio.knit.model.Needle
import kotlin.math.abs

/** A folder of needles. [brand] is empty for needles without a brand. */
data class NeedleGroup(val brand: String, val needles: List<Needle>) {
    val key: String get() = brandKey(brand)
}

/** Brands are compared without regard to case or surrounding spaces ("chiaogoo " = "ChiaoGoo"). */
fun brandKey(brand: String): String = brand.trim().lowercase()

private val bySize = compareBy<Needle>({ it.sizeMm }, { it.type.ordinal }, { it.lengthCm ?: 0.0 }, { it.material.lowercase() })

/**
 * Groups needles by brand: brands alphabetically, needles by size within each, needles without a
 * brand last. A brand is shown with the spelling used most often.
 */
fun groupByBrand(needles: List<Needle>): List<NeedleGroup> {
    val groups = needles.groupBy { brandKey(it.brand) }
    val named = groups.filterKeys { it.isNotEmpty() }.map { (_, list) ->
        val spelling = list.map { it.brand.trim() }.groupingBy { it }.eachCount().maxWith(compareBy({ it.value }, { e -> e.key.count { c -> c.isUpperCase() } })).key
        NeedleGroup(spelling, list.sortedWith(bySize))
    }.sortedBy { it.key }
    val unnamed = groups[""]?.let { listOf(NeedleGroup("", it.sortedWith(bySize))) }.orEmpty()
    return named + unnamed
}

/** Groups needles by diameter, smallest first; within a size by brand. */
fun groupBySize(needles: List<Needle>): List<Pair<Double, List<Needle>>> =
    needles.groupBy { kotlin.math.round(it.sizeMm * 100) / 100 }
        .entries.sortedBy { it.key }
        .map { (mm, list) -> mm to list.sortedWith(compareBy({ brandKey(it.brand).ifEmpty { "￿" } }, { it.type.ordinal }, { it.lengthCm ?: 0.0 })) }

/** All brands in use, for quick picking, in the spelling [groupByBrand] shows. */
fun knownBrands(needles: List<Needle>): List<String> = groupByBrand(needles).map { it.brand }.filter { it.isNotEmpty() }

/**
 * Needles whose brand was typed into the notes before there was a brand field: no brand yet and
 * a short, single-line note. Only these are offered for [moveNotesToBrand].
 */
fun needlesWithBrandInNotes(needles: List<Needle>): List<Needle> =
    needles.filter { it.brand.isBlank() && it.notes.isNotBlank() && '\n' !in it.notes && it.notes.trim().length <= 40 }

/** Moves the note into the brand field. Applied only after the user agrees. */
fun moveNotesToBrand(n: Needle): Needle = n.copy(brand = n.notes.trim(), notes = "")

/**
 * A new needle for a brand folder: same brand, type, material and length as the folder's needles,
 * in the next standard size that the folder does not have yet.
 */
fun nextInSet(group: List<Needle>, standardSizes: List<Double>, newId: String): Needle? {
    val last = group.maxByOrNull { it.sizeMm } ?: return null
    val taken = group.map { it.sizeMm }
    val next = standardSizes.firstOrNull { s -> s > last.sizeMm && taken.none { abs(it - s) < 0.01 } } ?: last.sizeMm
    return last.copy(id = newId, sizeMm = next, notes = "")
}
