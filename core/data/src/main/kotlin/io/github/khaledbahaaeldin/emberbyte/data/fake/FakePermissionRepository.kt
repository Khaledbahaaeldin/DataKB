package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.PermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.PermissionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePermissionRepository(
    initial: PermissionState = PermissionState(usageAccess = true, notifications = true, phoneState = true, vpnConsentGranted = false),
) : PermissionRepository {
    private val state = MutableStateFlow(initial)

    fun set(value: PermissionState) {
        state.value = value
    }

    override fun observe(): Flow<PermissionState> = state

    override suspend fun recheck() = Unit
}
