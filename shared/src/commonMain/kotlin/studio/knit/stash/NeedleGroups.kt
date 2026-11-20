package studio.knit.stash

import studio.knit.model.Cable
import studio.knit.model.Needle
import kotlin.math.abs

/** A folder of needles and cables. [brand] is empty for items without a brand. */
data class NeedleGroup(val brand: String, val needles: List<Needle>, val cables: List<Cable> = emptyList()) {
    val key: String get() = brandKey(brand)
}

/** Brands are compared without regard to case or surrounding spaces ("chiaogoo " = "ChiaoGoo"). */
fun brandKey(brand: String): String = brand.trim().lowercase()

private val bySize = compareBy<Needle>({ it.sizeMm }, { it.type.ordinal }, { it.lengthCm ?: 0.0 }, { it.material.lowercase() })

/**
 * Groups needles by brand: brands alphabetically, needles by size within each, needles without a
 * brand last. A brand is shown with the spelling used most often.
 */
fun groupByBrand(needles: List<Needle>, cables: List<Cable> = emptyList()): List<NeedleGroup> {
    val groups = needles.groupBy { brandKey(it.brand) }
    val cableGroups = cables.groupBy { brandKey(it.brand) }
    val keys = (groups.keys + cableGroups.keys).filter { it.isNotEmpty() }
    val named = keys.map { key ->
        val list = groups[key].orEmpty()
        val cableList = cableGroups[key].orEmpty()
        val spelling = (list.map { it.brand.trim() } + cableList.map { it.brand.trim() }).groupingBy { it }.eachCount().maxWith(compareBy({ it.value }, { e -> e.key.count { c -> c.isUpperCase() } })).key
        NeedleGroup(spelling, list.sortedWith(bySize), cableList.sortedWith(cableOrder))
    }.sortedBy { it.key }
    val unnamedNeedles = groups[""].orEmpty()
    val unnamedCables = cableGroups[""].orEmpty()
    val unnamed = if (unnamedNeedles.isEmpty() && unnamedCables.isEmpty()) emptyList()
    else listOf(NeedleGroup("", unnamedNeedles.sortedWith(bySize), unnamedCables.sortedWith(cableOrder)))
    return named + unnamed
}

/** Groups needles by diameter, smallest first; within a size by brand. */
fun groupBySize(needles: List<Needle>): List<Pair<Double, List<Needle>>> =
    needles.groupBy { kotlin.math.round(it.sizeMm * 100) / 100 }
        .entries.sortedBy { it.key }
        .map { (mm, list) -> mm to list.sortedWith(compareBy({ brandKey(it.brand).ifEmpty { "￿" } }, { it.type.ordinal }, { it.lengthCm ?: 0.0 })) }

private val cableOrder = compareBy<Cable>({ it.lengthCm }, { it.connector.lowercase() })

/** All brands in use, for quick picking, in the spelling [groupByBrand] shows. */
fun knownBrands(needles: List<Needle>, cables: List<Cable> = emptyList()): List<String> =
    groupByBrand(needles, cables).map { it.brand }.filter { it.isNotEmpty() }

/** Common cable lengths in centimetres, as sold for interchangeable sets. */
val CommonCableLengthsCm: List<Double> = listOf(20.0, 30.0, 40.0, 50.0, 60.0, 80.0, 100.0, 120.0, 150.0)

/** A new cable for a brand folder, with the brand's connector and the next common length it lacks. */
fun nextCable(group: List<Cable>, brand: String, newId: String): Cable {
    val last = group.maxByOrNull { it.lengthCm }
    val taken = group.map { it.lengthCm }
    val length = CommonCableLengthsCm.firstOrNull { l -> (last == null || l > last.lengthCm) && taken.none { abs(it - l) < 0.5 } }
        ?: CommonCableLengthsCm.firstOrNull { l -> taken.none { abs(it - l) < 0.5 } } ?: 80.0
    return Cable(id = newId, brand = brand, lengthCm = length, connector = last?.connector ?: "", material = last?.material ?: "")
}

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
