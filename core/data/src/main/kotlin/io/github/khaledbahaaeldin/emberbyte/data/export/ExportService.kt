package io.github.khaledbahaaeldin.emberbyte.data.export

import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import java.io.OutputStream
import java.time.Duration
import java.time.Instant

interface ExportService {
    suspend fun exportCsv(range: DateRange, out: OutputStream): Outcome<Unit>
    suspend fun exportJson(out: OutputStream): Outcome<Unit>
}

internal suspend fun UsageStore.forEachHourlyChunk(
    from: Instant,
    to: Instant,
    chunk: Duration = Duration.ofDays(7),
    action: suspend (List<HourlyUsage>) -> Unit,
) {
    var start = from
    while (start < to) {
        val end = minOf(start.plus(chunk), to)
        val rows = hourlyRows(start, end) // from inclusive, to exclusive
        if (rows.isNotEmpty()) action(rows)
        start = end
    }
}
