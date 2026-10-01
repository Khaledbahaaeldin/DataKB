package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.store.UsageRetention
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageStore
import io.github.khaledbahaaeldin.emberbyte.engine.counter.CounterReading
import io.github.khaledbahaaeldin.emberbyte.engine.model.CoverageGap
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import io.github.khaledbahaaeldin.emberbyte.engine.usage.MinuteTotal
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

private data class MinuteKey(val minuteStart: Instant, val network: NetworkKind, val subscriptionId: Int)
private data class HourKey(val hourStart: Instant, val uid: Int, val network: NetworkKind, val subscriptionId: Int)

/** Pure in-memory `UsageStore` with the same semantics as the Room one; used by tests and previews. */
class InMemoryUsageStore : UsageStore {
    private val minutes = MutableStateFlow<Map<MinuteKey, MinuteTotal>>(emptyMap())
    private val hours = MutableStateFlow<Map<HourKey, HourlyUsage>>(emptyMap())
    private val meta = MutableStateFlow<Map<Int, AppMeta>>(emptyMap())
    private val gapList = MutableStateFlow<List<CoverageGap>>(emptyList())
    private val checkpoints = MutableStateFlow<Map<String, CounterReading>>(emptyMap())

    override suspend fun addMinute(row: MinuteTotal) = minutes.update { map ->
        val key = MinuteKey(row.minuteStart, row.network, row.subscriptionId)
        val existing = map[key]
        map + (key to if (existing == null) row else existing.copy(
            rxBytes = existing.rxBytes + row.rxBytes,
            txBytes = existing.txBytes + row.txBytes,
        ))
    }

    override suspend fun upsertHourly(rows: List<HourlyUsage>) = hours.update { map ->
        map + rows.associateBy { HourKey(it.hourStart, it.uid, it.network, it.subscriptionId) }
    }

    override suspend fun upsertAppMeta(meta: List<AppMeta>) = this.meta.update { map -> map + meta.associateBy { it.uid } }

    override suspend fun insertGap(gap: CoverageGap) = gapList.update { it + gap }

    override suspend fun saveCheckpoint(source: String, reading: CounterReading) =
        checkpoints.update { it + (source to reading) }

    override suspend fun loadCheckpoint(source: String): CounterReading? = checkpoints.value[source]

    private fun minuteList(from: Instant, to: Instant) = minutes.value.values
        .filter { it.minuteStart >= from && it.minuteStart < to }
        .sortedWith(compareBy({ it.minuteStart }, { it.network }, { it.subscriptionId }))

    private fun hourList(map: Map<HourKey, HourlyUsage>, from: Instant, to: Instant) = map.values
        .filter { it.hourStart >= from && it.hourStart < to }
        .sortedWith(compareBy({ it.hourStart }, { it.uid }))

    private fun gapsIn(list: List<CoverageGap>, from: Instant, to: Instant) =
        list.filter { it.to >= from && it.from <= to }.sortedBy { it.from }

    override suspend fun minuteRows(from: Instant, to: Instant) = minuteList(from, to)

    override suspend fun hourlyRows(from: Instant, to: Instant) = hourList(hours.value, from, to)

    override suspend fun appMeta(): List<AppMeta> = meta.value.values.toList()

    override suspend fun gaps(from: Instant, to: Instant) = gapsIn(gapList.value, from, to)

    override fun observeMinuteRows(from: Instant, to: Instant): Flow<List<MinuteTotal>> =
        minutes.map { map -> map.values.filter { it.minuteStart >= from && it.minuteStart < to }
            .sortedWith(compareBy({ it.minuteStart }, { it.network }, { it.subscriptionId })) }.distinctUntilChanged()

    override fun observeHourlyRows(from: Instant, to: Instant): Flow<List<HourlyUsage>> =
        hours.map { hourList(it, from, to) }.distinctUntilChanged()

    override fun observeAppMeta(): Flow<List<AppMeta>> = meta.map { it.values.toList() }.distinctUntilChanged()

    override fun observeGaps(from: Instant, to: Instant): Flow<List<CoverageGap>> =
        gapList.map { gapsIn(it, from, to) }.distinctUntilChanged()

    override fun observeLastSample(): Flow<Instant?> =
        minutes.map { map -> map.values.maxOfOrNull { it.minuteStart } }.distinctUntilChanged()

    override suspend fun prune(now: Instant) {
        val minuteCut = now.minus(UsageRetention.MINUTES)
        val hourCut = now.minus(UsageRetention.HOURLY)
        val gapCut = now.minus(UsageRetention.GAPS)
        minutes.update { map -> map.filterValues { it.minuteStart >= minuteCut } }
        hours.update { map -> map.filterValues { it.hourStart >= hourCut } }
        gapList.update { list -> list.filter { it.to >= gapCut } }
    }
}
