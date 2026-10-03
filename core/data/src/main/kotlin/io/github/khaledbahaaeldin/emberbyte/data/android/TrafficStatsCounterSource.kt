package io.github.khaledbahaaeldin.emberbyte.data.android

import android.content.Context
import android.net.TrafficStats
import android.provider.Settings
import io.github.khaledbahaaeldin.emberbyte.data.source.CounterSnapshot
import io.github.khaledbahaaeldin.emberbyte.data.source.CounterSource
import java.time.Clock

class TrafficStatsCounterSource(
    private val context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : CounterSource {
    override fun read(): CounterSnapshot {
        val boots = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
        return CounterSnapshot(
            at = clock.instant(),
            mobileRxBytes = TrafficStats.getMobileRxBytes().orZero(),
            mobileTxBytes = TrafficStats.getMobileTxBytes().orZero(),
            totalRxBytes = TrafficStats.getTotalRxBytes().orZero(),
            totalTxBytes = TrafficStats.getTotalTxBytes().orZero(),
            bootId = boots.toString(),
        )
    }

    /** `TrafficStats.UNSUPPORTED` is -1. */
    private fun Long.orZero(): Long = if (this < 0L) 0L else this
}
