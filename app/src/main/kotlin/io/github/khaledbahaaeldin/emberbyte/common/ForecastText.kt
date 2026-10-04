package io.github.khaledbahaaeldin.emberbyte.common

import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastKind
import io.github.khaledbahaaeldin.emberbyte.ui.design.model.ForecastUi

/** One sentence for a forecast, used by Home, the plan card and the widgets. */
internal fun forecastSentence(ui: ForecastUi): String = when (ui.kind) {
    ForecastKind.Safe, ForecastKind.Out, ForecastKind.ProbablySafe -> ui.detail
    ForecastKind.RunsOut -> ui.detail.replace("runs out", "runs out ${ui.headline}")
}
