package io.github.khaledbahaaeldin.emberbyte.data

import kotlinx.coroutines.flow.Flow

data class PermissionState(
    val usageAccess: Boolean,
    val notifications: Boolean,
    val phoneState: Boolean,
    val vpnConsentGranted: Boolean,
)

interface PermissionRepository {
    fun observe(): Flow<PermissionState>
    suspend fun recheck()
}
