package studio.knit

import kotlinx.serialization.json.Json
import studio.knit.model.Cable
import studio.knit.model.Needle
import studio.knit.model.NeedleType
import studio.knit.stash.groupByBrand
import studio.knit.stash.groupBySize
import studio.knit.stash.knownBrands
import studio.knit.stash.moveNotesToBrand
import studio.knit.stash.needlesWithBrandInNotes
import studio.knit.stash.nextCable
import studio.knit.stash.nextInSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NeedleGroupsTest {
    private fun n(mm: Double, brand: String = "", notes: String = "", material: String = "") =
        Needle(id = id(), sizeMm = mm, type = NeedleType.INTERCHANGEABLE, material = material, lengthCm = 12.0, notes = notes, brand = brand)

    @Test fun groupsByBrandIgnoringCaseWithUnbrandedLast() {
        val groups = groupByBrand(listOf(n(4.0, "Seeknit"), n(2.75, "ChiaoGoo"), n(3.0), n(2.0, "seeknit "), n(1.5, "ChiaoGoo")))
        assertEquals(listOf("ChiaoGoo", "Seeknit", ""), groups.map { it.brand })
        assertEquals(listOf(1.5, 2.75), groups[0].needles.map { it.sizeMm })
        assertEquals(listOf(2.0, 4.0), groups[1].needles.map { it.sizeMm })
        assertEquals(listOf("ChiaoGoo", "Seeknit"), knownBrands(groups.flatMap { it.needles }))
    }

    @Test fun groupsBySizeWithBrandsInside() {
        val bySize = groupBySize(listOf(n(4.0, "Seeknit"), n(4.0, "ChiaoGoo"), n(2.75, "ChiaoGoo"), n(4.0)))
        assertEquals(listOf(2.75, 4.0), bySize.map { it.first })
        assertEquals(listOf("ChiaoGoo", "Seeknit", ""), bySize[1].second.map { it.brand })
    }

    @Test fun brandsTypedInNotesCanBeMovedOnRequest() {
        val list = listOf(n(4.0, notes = "ChiaoGoo"), n(3.0, notes = "for the ribbing\nof the sleeves"), n(2.0, brand = "Seeknit", notes = "Seeknit"))
        val candidates = needlesWithBrandInNotes(list)
        assertEquals(listOf(4.0), candidates.map { it.sizeMm })
        val moved = moveNotesToBrand(candidates.single())
        assertEquals("ChiaoGoo" to "", moved.brand to moved.notes)
    }

    @Test fun nextNeedleInASetTakesTheNextFreeSize() {
        val set = listOf(n(2.0, "ChiaoGoo", material = "Steel"), n(2.5, "ChiaoGoo", material = "Steel"))
        val next = assertNotNull(nextInSet(set, listOf(2.0, 2.25, 2.5, 2.75, 3.0), "new"))
        assertEquals(2.75, next.sizeMm)
        assertEquals("ChiaoGoo" to "Steel", next.brand to next.material)
        assertEquals("new", next.id)
    }

    @Test fun stashSavedBeforeTheBrandFieldStillLoads() {
        val old = """{"id":"x","sizeMm":4.0,"type":"INTERCHANGEABLE","material":"Steel","lengthCm":12.0,"notes":"ChiaoGoo"}"""
        val needle = Json { ignoreUnknownKeys = true }.decodeFromString(Needle.serializer(), old)
        assertEquals("" to "ChiaoGoo", needle.brand to needle.notes)
        assertTrue(needlesWithBrandInNotes(listOf(needle)).isNotEmpty())
    }

    @Test fun cablesJoinTheirBrandFolder() {
        val cables = listOf(Cable("c1", "ChiaoGoo", 80.0, "[S]"), Cable("c2", "chiaogoo", 40.0, "[S]"), Cable("c3", "Addi", 60.0))
        val groups = groupByBrand(listOf(n(4.0, "ChiaoGoo"), n(3.0, "Seeknit")), cables)
        assertEquals(listOf("Addi", "ChiaoGoo", "Seeknit"), groups.map { it.brand })
        val chiao = groups.single { it.brand == "ChiaoGoo" }
        assertEquals(listOf(40.0, 80.0), chiao.cables.map { it.lengthCm })
        assertEquals(1, chiao.needles.size)
        // A brand with only cables still gets its folder.
        assertEquals(0, groups.single { it.brand == "Addi" }.needles.size)
        assertEquals(listOf("Addi", "ChiaoGoo", "Seeknit"), knownBrands(groups.flatMap { it.needles }, cables))
    }

    @Test fun nextCableTakesTheNextCommonLengthAndConnector() {
        val next = nextCable(listOf(Cable("a", "ChiaoGoo", 40.0, "[S]", material = "Red lace"), Cable("b", "ChiaoGoo", 60.0, "[S]")), "ChiaoGoo", "new")
        assertEquals(80.0, next.lengthCm)
        assertEquals("[S]" to "ChiaoGoo", next.connector to next.brand)
        assertEquals(20.0, nextCable(emptyList(), "Addi", "x").lengthCm)
    }
}
