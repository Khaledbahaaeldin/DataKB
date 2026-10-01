package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.PermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePermissionRepository : PermissionRepository {
    private val state = MutableStateFlow(
        PermissionState(usageAccess = true, notifications = true, phoneState = true, vpnConsentGranted = false),
    )

    override fun observe(): Flow<PermissionState> = state

    override suspend fun recheck() = Unit
}
