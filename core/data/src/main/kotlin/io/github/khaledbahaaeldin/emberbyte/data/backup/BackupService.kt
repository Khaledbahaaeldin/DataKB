package io.github.khaledbahaaeldin.emberbyte.data.backup

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.AlertRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.AppBudget
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.NetworkKind
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

data class RestoreSummary(
    val plans: Int,
    val budgets: Int,
    val usageRows: Int,
    val skippedUsageRows: Int,
)

interface BackupService {
    suspend fun createBackup(out: OutputStream, passphrase: CharArray): Outcome<Unit>
    suspend fun restoreBackup(input: InputStream, passphrase: CharArray): Outcome<RestoreSummary>
}

data class RestoredPlan(
    val plan: Plan,
    val addOns: List<AddOn>,
    val freeRules: List<FreeRule>,
    val alertRules: List<AlertRule>,
)

data class ParsedBackup(
    val contractVersion: Int,
    val plans: List<RestoredPlan>,
    val budgets: List<AppBudget>,
    val usage: Sequence<ParsedUsageRow>,
)

data class ParsedUsageRow(
    val hourStart: Instant,
    val packageName: String,
    val network: NetworkKind,
    val subscriptionId: Int,
    val rxBytes: Long,
    val txBytes: Long,
)
