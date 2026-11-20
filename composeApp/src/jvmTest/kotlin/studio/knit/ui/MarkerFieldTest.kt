package studio.knit.ui

import studio.knit.ui.written.groupsToMarkers
import studio.knit.ui.written.markersToGroups
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkerFieldTest {
    @Test fun groupsAndPositionsConvertBothWays() {
        assertEquals(listOf(2, 4), groupsToMarkers("2, 2, 2", 6))
        assertEquals(listOf(2, 4), groupsToMarkers("2, 2, 2", null))
        assertEquals(listOf(10, 30), groupsToMarkers("10 20", 50))
        assertEquals("2, 2, 2", markersToGroups(listOf(2, 4), 6))
        assertEquals("", markersToGroups(emptyList(), 6))
        assertEquals(emptyList(), groupsToMarkers("", 6))
    }
}
