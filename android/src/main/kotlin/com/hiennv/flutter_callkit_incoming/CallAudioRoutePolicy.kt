package com.hiennv.flutter_callkit_incoming

import android.telecom.CallAudioState

internal object CallAudioRoutePolicy {
    fun select(speaker: Boolean, available: Set<Int>): Int? {
        val priority = if (speaker) listOf(CallAudioState.ROUTE_SPEAKER) else listOf(
            CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_WIRED_HEADSET,
            CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_SPEAKER)
        return priority.firstOrNull { it in available }
    }

    fun connection(states: Map<String, String>, available: Set<String>): String? =
        available.firstOrNull { states[it] == "active" }
            ?: available.singleOrNull()?.takeIf { states[it] in setOf("held", "holding", "connecting", "resuming") }
}
