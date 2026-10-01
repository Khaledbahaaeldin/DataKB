package io.github.khaledbahaaeldin.emberbyte

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.khaledbahaaeldin.emberbyte.data.PermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.PlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.SettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.UsageRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePermissionRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakePlanRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeSettingsRepository
import io.github.khaledbahaaeldin.emberbyte.data.fake.FakeUsageRepository
import io.github.khaledbahaaeldin.emberbyte.home.HomeViewModel

/** Hand-written dependency graph. Hilt replaces this in M2 when real services need injection. */
class AppGraph {
    val usage: UsageRepository = FakeUsageRepository()
    val plans: PlanRepository = FakePlanRepository()
    val settings: SettingsRepository = FakeSettingsRepository()
    val permissions: PermissionRepository = FakePermissionRepository()

    fun homeViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer { HomeViewModel(usage, plans, settings) }
    }
}
