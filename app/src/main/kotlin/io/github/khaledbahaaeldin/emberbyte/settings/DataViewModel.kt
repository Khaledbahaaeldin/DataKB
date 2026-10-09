package io.github.khaledbahaaeldin.emberbyte.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.khaledbahaaeldin.emberbyte.data.backup.BackupService
import io.github.khaledbahaaeldin.emberbyte.data.backup.RestoreSummary
import io.github.khaledbahaaeldin.emberbyte.data.export.ExportService
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageRetention
import io.github.khaledbahaaeldin.emberbyte.data.util.SystemZoneClock
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import java.text.NumberFormat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExportRange { LAST_7_DAYS, LAST_30_DAYS, ALL }

data class DataUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

class DataViewModel(
    val export: ExportService,
    val backup: BackupService,
    val opener: StreamOpener,
    val clock: Clock = SystemZoneClock(),
    private val workContext: CoroutineContext = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    private var heldPassphrase: CharArray? = null

    private fun tryStart(): Boolean {
        var started = false
        _uiState.update { current ->
            if (current.busy) {
                started = false
                current
            } else {
                started = true
                current.copy(busy = true, message = null, isError = false)
            }
        }
        return started
    }

    fun holdPassphrase(passphrase: CharArray) {
        heldPassphrase?.fill('\u0000')
        heldPassphrase = passphrase
    }

    fun takeHeldPassphrase(): CharArray? {
        val held = heldPassphrase
        heldPassphrase = null
        return held
    }

    fun discardHeldPassphrase() {
        heldPassphrase?.fill('\u0000')
        heldPassphrase = null
    }

    public override fun onCleared() {
        heldPassphrase?.fill('\u0000')
        heldPassphrase = null
    }

    fun exportCsv(range: ExportRange, uri: Uri) {
        if (!tryStart()) return
        viewModelScope.launch(workContext) {
            val zone = clock.zone
            val now = clock.instant()
            val today = now.atZone(zone).toLocalDate()
            val dateRange = when (range) {
                ExportRange.LAST_7_DAYS -> DateRange(
                    from = today.minusDays(7).atStartOfDay(zone).toInstant(),
                    to = now.plusSeconds(3600),
                )
                ExportRange.LAST_30_DAYS -> DateRange(
                    from = today.minusDays(30).atStartOfDay(zone).toInstant(),
                    to = now.plusSeconds(3600),
                )
                ExportRange.ALL -> DateRange(
                    from = clock.instant().minus(UsageRetention.HOURLY).truncatedTo(ChronoUnit.DAYS),
                    to = now.plusSeconds(7200),
                )
            }

            try {
                val stream = opener.output(uri)
                stream.use { out ->
                    when (val outcome = export.exportCsv(dateRange, out)) {
                        is Outcome.Success -> {
                            _uiState.update { it.copy(busy = false, message = "Usage exported", isError = false) }
                        }
                        is Outcome.Failure -> {
                            _uiState.update { it.copy(busy = false, message = mapExportError(outcome.error), isError = true) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(busy = false) }
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(busy = false, message = "Could not open the file", isError = true) }
            }
        }
    }

    fun exportJson(uri: Uri) {
        if (!tryStart()) return
        viewModelScope.launch(workContext) {
            try {
                val stream = opener.output(uri)
                stream.use { out ->
                    when (val outcome = export.exportJson(out)) {
                        is Outcome.Success -> {
                            _uiState.update { it.copy(busy = false, message = "Everything exported as JSON", isError = false) }
                        }
                        is Outcome.Failure -> {
                            _uiState.update { it.copy(busy = false, message = mapExportError(outcome.error), isError = true) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(busy = false) }
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(busy = false, message = "Could not open the file", isError = true) }
            }
        }
    }

    fun createBackup(uri: Uri, passphrase: CharArray) {
        if (!tryStart()) {
            passphrase.fill('\u0000')
            return
        }
        viewModelScope.launch(workContext) {
            try {
                val stream = opener.output(uri)
                stream.use { out ->
                    when (val outcome = backup.createBackup(out, passphrase)) {
                        is Outcome.Success -> {
                            _uiState.update { it.copy(busy = false, message = "Backup saved", isError = false) }
                        }
                        is Outcome.Failure -> {
                            val msg = when (val err = outcome.error) {
                                is EmberbyteError.Invalid -> err.reason
                                is EmberbyteError.Storage -> if (err.cause.startsWith("Backup failed")) err.cause else "Backup failed: ${err.cause}"
                                is EmberbyteError.Unexpected -> "Backup failed: ${err.cause}"
                                else -> "Backup failed"
                            }
                            _uiState.update { it.copy(busy = false, message = msg, isError = true) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(busy = false) }
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(busy = false, message = "Could not open the file", isError = true) }
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    fun restoreBackup(uri: Uri, passphrase: CharArray) {
        if (!tryStart()) {
            passphrase.fill('\u0000')
            return
        }
        viewModelScope.launch(workContext) {
            try {
                val stream = opener.input(uri)
                stream.use { input ->
                    when (val outcome = backup.restoreBackup(input, passphrase)) {
                        is Outcome.Success -> {
                            val summary = outcome.value
                            _uiState.update {
                                it.copy(
                                    busy = false,
                                    message = formatRestoreSummary(summary),
                                    isError = false,
                                )
                            }
                        }
                        is Outcome.Failure -> {
                            val msg = when (val err = outcome.error) {
                                is EmberbyteError.Invalid -> when (err.field) {
                                    "passphrase" -> "Wrong passphrase or damaged file"
                                    else -> err.reason
                                }
                                is EmberbyteError.Storage -> if (err.cause.startsWith("Restore failed")) err.cause else "Restore failed: ${err.cause}"
                                is EmberbyteError.Unexpected -> "Restore failed: ${err.cause}"
                                else -> "Restore failed"
                            }
                            _uiState.update { it.copy(busy = false, message = msg, isError = true) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(busy = false) }
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(busy = false, message = "Could not open the file", isError = true) }
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    fun showMessage(message: String, isError: Boolean = false) {
        _uiState.update { it.copy(message = message, isError = isError) }
    }

    private fun mapExportError(error: EmberbyteError): String = when (error) {
        is EmberbyteError.Storage -> "Export failed: ${error.cause}"
        is EmberbyteError.Unexpected -> "Export failed: ${error.cause}"
        is EmberbyteError.Invalid -> error.reason
        else -> "Export failed"
    }

    private fun formatRestoreSummary(summary: RestoreSummary): String {
        val nf = NumberFormat.getIntegerInstance(Locale.ENGLISH)
        val plansPart = "${nf.format(summary.plans)} ${if (summary.plans == 1) "plan" else "plans"}"
        val budgetsPart = "${nf.format(summary.budgets)} ${if (summary.budgets == 1) "budget" else "budgets"}"
        val usagePart = "${nf.format(summary.usageRows)} ${if (summary.usageRows == 1) "usage row" else "usage rows"}"
        val skippedPart = if (summary.skippedUsageRows > 0) {
            " (${nf.format(summary.skippedUsageRows)} skipped because the app is not installed)"
        } else {
            ""
        }
        return "Restored $plansPart, $budgetsPart and $usagePart$skippedPart"
    }
}
