package com.hiennv.flutter_callkit_incoming

import org.junit.Assert.*
import org.junit.Test

class PendingCallRouteTest {
    @Test fun firstTargetedMirrorConsumesTheRequestOnce() {
        val route = PendingCallRoute()
        route.request(true, listOf("a"))
        assertNull(route.take("b"))
        assertEquals(true, route.take("a"))
        // A later endpoint change on the same mirror must not replay it.
        assertNull(route.take("a"))
    }

    @Test fun requestWithoutTargetsAndEndedTargetsAreDropped() {
        val route = PendingCallRoute()
        route.request(true, emptyList())
        assertNull(route.take("a"))

        route.request(false, listOf("a"))
        route.retain(setOf("b"))
        assertNull(route.take("a"))
        assertNull(route.take("b"))
    }

    @Test fun newerRequestReplacesOlderAndClearDropsIt() {
        val route = PendingCallRoute()
        route.request(true, listOf("a"))
        route.request(false, listOf("a", "b"))
        route.retain(setOf("a", "b", "c"))
        assertEquals(false, route.take("b"))
        route.request(true, listOf("a"))
        route.clear()
        assertNull(route.take("a"))
    }
}
