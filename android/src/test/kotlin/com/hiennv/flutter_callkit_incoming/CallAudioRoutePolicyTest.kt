package com.hiennv.flutter_callkit_incoming

import android.telecom.CallAudioState.*
import org.junit.Assert.*
import org.junit.Test

class CallAudioRoutePolicyTest {
    @Test fun speakerOverridesBluetoothAndPreferredRestoresHeadset() {
        val routes = setOf(ROUTE_EARPIECE, ROUTE_SPEAKER, ROUTE_BLUETOOTH, ROUTE_WIRED_HEADSET)
        assertEquals(ROUTE_SPEAKER, CallAudioRoutePolicy.select(true, routes))
        assertEquals(ROUTE_BLUETOOTH, CallAudioRoutePolicy.select(false, routes))
        assertEquals(ROUTE_WIRED_HEADSET, CallAudioRoutePolicy.select(false, routes - ROUTE_BLUETOOTH))
        assertEquals(ROUTE_EARPIECE, CallAudioRoutePolicy.select(false, setOf(ROUTE_SPEAKER, ROUTE_EARPIECE)))
        assertEquals(ROUTE_SPEAKER, CallAudioRoutePolicy.select(false, setOf(ROUTE_SPEAKER)))
        assertNull(CallAudioRoutePolicy.select(true, setOf(ROUTE_EARPIECE)))
    }

    @Test fun routeTargetsActiveConnectionAndNeverGuessesBetweenHeldCalls() {
        assertEquals("b", CallAudioRoutePolicy.connection(mapOf("a" to "held", "b" to "active"), setOf("a", "b")))
        assertEquals("a", CallAudioRoutePolicy.connection(mapOf("a" to "held"), setOf("a")))
        assertEquals("a", CallAudioRoutePolicy.connection(mapOf("a" to "connecting"), setOf("a")))
        assertNull(CallAudioRoutePolicy.connection(mapOf("a" to "held", "b" to "held"), setOf("a", "b")))
        assertNull(CallAudioRoutePolicy.connection(mapOf("a" to "active"), emptySet()))
    }
}
