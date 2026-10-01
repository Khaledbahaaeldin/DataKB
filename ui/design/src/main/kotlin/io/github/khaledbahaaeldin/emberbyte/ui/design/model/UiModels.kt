package io.github.khaledbahaaeldin.emberbyte.ui.design.model

enum class NetworkKindUi { Mobile, Wifi }

data class ForecastUi(val headline: String, val detail: String, val confidence: String)

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
