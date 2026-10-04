package io.github.khaledbahaaeldin.emberbyte.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionRequestPolicyTest {
    @Test
    fun decide_combinations() {
        assertEquals(PermissionDecision.Launch, PermissionRequestPolicy.decide(asked = false, shouldShowRationale = false))
        assertEquals(PermissionDecision.Launch, PermissionRequestPolicy.decide(asked = false, shouldShowRationale = true))
        assertEquals(PermissionDecision.Launch, PermissionRequestPolicy.decide(asked = true, shouldShowRationale = true))
        assertEquals(PermissionDecision.OpenSettings, PermissionRequestPolicy.decide(asked = true, shouldShowRationale = false))
    }
}
