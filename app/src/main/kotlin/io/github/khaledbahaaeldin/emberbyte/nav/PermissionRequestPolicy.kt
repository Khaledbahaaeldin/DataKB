package io.github.khaledbahaaeldin.emberbyte.nav

internal enum class PermissionDecision { Launch, OpenSettings }

internal object PermissionRequestPolicy {
    fun decide(asked: Boolean, shouldShowRationale: Boolean): PermissionDecision =
        if (!asked || shouldShowRationale) PermissionDecision.Launch else PermissionDecision.OpenSettings
}
