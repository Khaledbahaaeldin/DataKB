package io.github.khaledbahaaeldin.emberbyte.ui.design.model

enum class NetworkKindUi { Mobile, Wifi }

enum class ForecastKind { RunsOut, Safe, Out, ProbablySafe }

data class ForecastUi(
    val headline: String,
    val detail: String,
    val confidence: String,
    val kind: ForecastKind = ForecastKind.RunsOut,
)

data class AppRowUi(
    val packageName: String,
    val label: String,
    val mobileBytes: Long,
    val wifiBytes: Long,
) {
    val totalBytes: Long get() = mobileBytes + wifiBytes
}

/** [description] is the spoken form, e.g. "Wednesday, 2.10 gigabytes". */
data class BarUi(val label: String, val bytes: Long, val description: String)

data class PermissionPromptUi(val id: String, val title: String, val body: String, val actionLabel: String)

data class GapUi(val message: String)

enum class BudgetStateUi { Ok, Warning, Exceeded }

data class PlanChipUi(val id: Long, val name: String, val selected: Boolean)

data class PlanCardUi(
    val id: Long,
    val name: String,
    val simLabel: String?,
    val fraction: Float,
    val ringState: BudgetStateUi,
    val percentText: String,
    val usedText: String,
    val remainingText: String,
    val daysLeftText: String,
    val forecastText: String?,
    val isEstimated: Boolean,
    val chips: List<String>,
    val archived: Boolean,
)
