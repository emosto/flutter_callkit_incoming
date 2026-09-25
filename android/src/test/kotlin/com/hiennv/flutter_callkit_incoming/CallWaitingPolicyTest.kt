package com.hiennv.flutter_callkit_incoming

import org.junit.Assert.*
import org.junit.Test

class CallWaitingPolicyTest {
    @Test fun firstCallIsAllowedEvenWhenWaitingIsDisabled() {
        assertFalse(CallWaitingPolicy.dismiss(0, false, false))
    }
    @Test fun waitingSettingAndTwoCallCapacityAreEnforced() {
        assertFalse(CallWaitingPolicy.dismiss(1, true, false))
        assertTrue(CallWaitingPolicy.dismiss(1, false, false))
        assertTrue(CallWaitingPolicy.dismiss(2, true, false))
    }
    @Test fun aDismissedDuplicateCannotRingAgain() {
        assertTrue(CallWaitingPolicy.dismiss(0, true, true))
    }
}
