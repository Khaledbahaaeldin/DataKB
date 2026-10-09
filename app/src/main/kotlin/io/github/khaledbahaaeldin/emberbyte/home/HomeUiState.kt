package io.github.khaledbahaaeldin.emberbyte.home

import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.AppRowUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.BarUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.GapUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.NetworkKindUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PermissionPromptUi
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.PlanChipUi

data class SpikeTileUi(
    val id: Long,
    val packageName: String,
    val title: String,
    val detail: String,
)

data class HomeUiState(
    val heroLabel: String = "Today",
    val heroBytes: Long = 0L,
    val heroDescription: String = "",
    val isToday: Boolean = true,
    val subtitle: String? = null,
    val isEstimated: Boolean = false,
    val throughputBps: Long = 0L,
    val liveRxBps: Long = 0L,
    val liveTxBps: Long = 0L,
    val network: NetworkKindUi? = null,
    val mobileBytes: Long = 0L,
    val wifiBytes: Long = 0L,
    val forecast: ForecastUi? = null,
    val topApps: List<AppRowUi> = emptyList(),
    val topAppsLocked: Boolean = false,
    val week: List<BarUi> = emptyList(),
    val selectedDay: Int? = null,
    val prompts: List<PermissionPromptUi> = emptyList(),
    val gap: GapUi? = null,
    val hasPlan: Boolean = false,
    val units: ByteUnits = ByteUnits.DECIMAL,
    val planChips: List<PlanChipUi> = emptyList(),
    val spike: SpikeTileUi? = null,
)

sealed interface HomeEvent {
    /** Tapping the selected bar again returns to "Today". */
    data class SelectDay(val index: Int) : HomeEvent
    data class SelectPlan(val planId: Long) : HomeEvent
    data class DismissSpike(val id: Long) : HomeEvent
}
