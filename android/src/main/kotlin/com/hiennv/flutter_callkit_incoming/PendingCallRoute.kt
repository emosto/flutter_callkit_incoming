package com.hiennv.flutter_callkit_incoming

/**
 * A route chosen while no Telecom mirror can route yet (none exists, or Telecom
 * has not reported its outputs). The app routes through WebRTC meanwhile;
 * Telecom would reset that route when the mirror's route is set up, so the
 * first targeted mirror re-applies it once.
 */
internal class PendingCallRoute {
    private var speaker: Boolean? = null
    private val calls = mutableSetOf<String>()

    fun request(speaker: Boolean, targets: Collection<String>) {
        clear()
        calls.addAll(targets)
        if (calls.isNotEmpty()) this.speaker = speaker
    }

    /** Drops calls that ended before their mirror appeared. */
    fun retain(live: Set<String>) {
        calls.retainAll(live)
        if (calls.isEmpty()) speaker = null
    }

    /** The first targeted mirror consumes the request; later calls return null. */
    fun take(id: String): Boolean? {
        val requested = speaker ?: return null
        if (id !in calls) return null
        clear()
        return requested
    }

    fun clear() {
        speaker = null
        calls.clear()
    }
}
