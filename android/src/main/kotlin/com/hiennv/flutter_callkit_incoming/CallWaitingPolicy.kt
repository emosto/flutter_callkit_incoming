package com.hiennv.flutter_callkit_incoming

internal object CallWaitingPolicy {
    fun dismiss(existingCount: Int, enabled: Boolean, previouslyDismissed: Boolean): Boolean =
        previouslyDismissed || existingCount >= 2 || (existingCount > 0 && !enabled)
}
