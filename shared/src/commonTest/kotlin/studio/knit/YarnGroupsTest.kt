package studio.knit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import studio.knit.model.Yarn
import studio.knit.stash.groupYarnsByBrand
import studio.knit.stash.knownYarnBrands
import studio.knit.stash.ownedSkeins
import studio.knit.stash.yarnForPattern

class YarnGroupsTest {
    private val stash = listOf(
        Yarn(id = "1", brand = "Holst", name = "Supersoft", color = "Teal", skeins = 4.0),
        Yarn(id = "2", brand = "holst ", name = "Coast", skeins = 2.0),
        Yarn(id = "3", brand = "Drops", name = "Alpaca"),
        Yarn(id = "4", name = "Leftovers", skeins = 1.0),
        Yarn(id = "5", brand = "Holst", name = "Supersoft", color = "Rust", skeins = 3.0),
    )

    @Test fun yarnIsGroupedByBrandIgnoringCase() {
        val g = groupYarnsByBrand(stash)
        assertEquals(listOf("Drops", "Holst", ""), g.map { it.brand })
        assertEquals(listOf("2", "5", "1"), g[1].yarns.map { it.id }) // Coast, Supersoft Rust, Supersoft Teal
        assertEquals(9.0, g[1].skeins)
        assertNull(g[0].skeins)
        assertEquals(listOf("Drops", "Holst"), knownYarnBrands(stash))
    }

    @Test fun addingStashYarnToAPatternDoesNotCopyTheSkeinsOwned() {
        val y = yarnForPattern(stash[0].copy(photoId = "img"), "new")
        assertEquals("new", y.id)
        assertNull(y.skeins)
        assertNull(y.photoId)
        assertEquals("Supersoft", y.name)
        assertEquals("Teal", y.color)
        // The sheet can still say how many she owns.
        assertEquals(4.0, ownedSkeins(y.copy(label = "MC"), stash))
        assertNull(ownedSkeins(y.copy(color = "Pink"), stash))
    }
}
