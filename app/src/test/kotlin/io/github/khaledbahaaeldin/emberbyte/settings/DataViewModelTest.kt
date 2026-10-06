package io.github.khaledbahaaeldin.emberbyte.settings

import android.net.Uri
import io.github.khaledbahaaeldin.emberbyte.data.backup.BackupService
import io.github.khaledbahaaeldin.emberbyte.data.backup.RestoreSummary
import io.github.khaledbahaaeldin.emberbyte.data.export.ExportService
import io.github.khaledbahaaeldin.emberbyte.data.store.UsageRetention
import io.github.khaledbahaaeldin.emberbyte.engine.model.DateRange
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC)
    private val export = FakeExportService()
    private val backup = FakeBackupService()
    private val opener = FakeStreamOpener()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel(
        customOpener: StreamOpener = opener,
        customClock: Clock = clock,
        workContext: CoroutineContext = dispatcher,
    ) = DataViewModel(export, backup, customOpener, customClock, workContext)

    @Test
    fun export_csv_last_7_days_calls_service_with_expected_range_and_updates_state() = runTest(dispatcher) {
        val vm = createViewModel()
        val uri = Uri.parse("content://fake/csv")
        vm.exportCsv(ExportRange.LAST_7_DAYS, uri)
        advanceUntilIdle()

        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), export.lastCsvRange?.from)
        assertEquals(Instant.parse("2026-10-08T13:00:00Z"), export.lastCsvRange?.to)
        assertEquals("Usage exported", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isError)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun export_csv_all_calls_service_with_retention_start_rounded_down_to_day() = runTest(dispatcher) {
        val vm = createViewModel()
        val uri = Uri.parse("content://fake/csv")
        vm.exportCsv(ExportRange.ALL, uri)
        advanceUntilIdle()

        val expectedStart = clock.instant().minus(UsageRetention.HOURLY).truncatedTo(ChronoUnit.DAYS)
        assertEquals(expectedStart, export.lastCsvRange?.from)
        assertEquals(clock.instant().plusSeconds(7200), export.lastCsvRange?.to)
        assertEquals("Usage exported", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isError)
    }

    @Test
    fun export_csv_busy_state_is_true_while_service_runs_and_false_after() = runTest(dispatcher) {
        val deferred = CompletableDeferred<Outcome<Unit>>()
        export.deferredCsv = deferred
        val vm = createViewModel()
        val uri = Uri.parse("content://fake/csv")
        vm.exportCsv(ExportRange.LAST_7_DAYS, uri)
        testScheduler.runCurrent()

        assertTrue(vm.uiState.value.busy)

        deferred.complete(Outcome.Success(Unit))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.busy)
        assertEquals("Usage exported", vm.uiState.value.message)
    }

    @Test
    fun export_json_success_sets_everything_exported_as_json() = runTest(dispatcher) {
        val vm = createViewModel()
        val uri = Uri.parse("content://fake/json")
        vm.exportJson(uri)
        advanceUntilIdle()

        assertTrue(export.exportJsonCalled)
        assertEquals("Everything exported as JSON", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isError)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun export_json_failure_with_storage_disk_full_sets_error() = runTest(dispatcher) {
        export.jsonOutcome = Outcome.Failure(EmberbyteError.Storage("disk full"))
        val vm = createViewModel()
        val uri = Uri.parse("content://fake/json")
        vm.exportJson(uri)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isError)
        assertEquals("Export failed: disk full", vm.uiState.value.message)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun create_backup_success_sets_backup_saved() = runTest(dispatcher) {
        val vm = createViewModel()
        val uri = Uri.parse("content://fake/backup")
        val passphrase = "mypassphrase123".toCharArray()
        vm.createBackup(uri, passphrase)
        advanceUntilIdle()

        assertTrue(backup.createBackupCalled)
        assertEquals("Backup saved", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isError)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun create_backup_invalid_passphrase_sets_reason_as_message() = runTest(dispatcher) {
        backup.createBackupOutcome = Outcome.Failure(EmberbyteError.Invalid("passphrase", "Use at least 8 characters"))
        val vm = createViewModel()
        val uri = Uri.parse("content://fake/backup")
        vm.createBackup(uri, "short".toCharArray())
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isError)
        assertEquals("Use at least 8 characters", vm.uiState.value.message)
    }

    @Test
    fun restore_backup_success_formats_summary_with_all_counts() = runTest(dispatcher) {
        backup.restoreBackupOutcome = Outcome.Success(
            RestoreSummary(plans = 2, budgets = 1, usageRows = 3120, skippedUsageRows = 14)
        )
        val vm = createViewModel()
        val uri = Uri.parse("content://fake/backup")
        vm.restoreBackup(uri, "mypassphrase123".toCharArray())
        advanceUntilIdle()

        assertTrue(backup.restoreBackupCalled)
        assertFalse(vm.uiState.value.isError)
        assertEquals(
            "Restored 2 plans, 1 budget and 3,120 usage rows (14 skipped because the app is not installed)",
            vm.uiState.value.message,
        )
    }

    @Test
    fun restore_backup_success_singular_without_skipped() = runTest(dispatcher) {
        backup.restoreBackupOutcome = Outcome.Success(
            RestoreSummary(plans = 1, budgets = 2, usageRows = 1, skippedUsageRows = 0)
        )
        val vm = createViewModel()
        val uri = Uri.parse("content://fake/backup")
        vm.restoreBackup(uri, "mypassphrase123".toCharArray())
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isError)
        assertEquals("Restored 1 plan, 2 budgets and 1 usage row", vm.uiState.value.message)
    }

    @Test
    fun restore_backup_invalid_passphrase_sets_expected_error_message() = runTest(dispatcher) {
        backup.restoreBackupOutcome = Outcome.Failure(EmberbyteError.Invalid("passphrase", "Bad key"))
        val vm = createViewModel()
        val uri = Uri.parse("content://fake/backup")
        vm.restoreBackup(uri, "wrongpass".toCharArray())
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isError)
        assertEquals("Wrong passphrase or damaged file", vm.uiState.value.message)
    }

    @Test
    fun restore_backup_invalid_file_sets_reason_as_message() = runTest(dispatcher) {
        backup.restoreBackupOutcome = Outcome.Failure(EmberbyteError.Invalid("file", "The backup is damaged"))
        val vm = createViewModel()
        val uri = Uri.parse("content://fake/backup")
        vm.restoreBackup(uri, "passphrase123".toCharArray())
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isError)
        assertEquals("The backup is damaged", vm.uiState.value.message)
    }

    @Test
    fun stream_opener_throwing_sets_could_not_open_file_and_does_not_call_service() = runTest(dispatcher) {
        val brokenOpener = FakeStreamOpener(throwOnOutput = FileNotFoundException("file not found"))
        val vm = createViewModel(customOpener = brokenOpener)
        val uri = Uri.parse("content://fake/broken")
        vm.exportCsv(ExportRange.LAST_7_DAYS, uri)
        advanceUntilIdle()

        assertNull(export.lastCsvRange)
        assertTrue(vm.uiState.value.isError)
        assertEquals("Could not open the file", vm.uiState.value.message)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun clear_message_resets_message_and_is_error() = runTest(dispatcher) {
        export.jsonOutcome = Outcome.Failure(EmberbyteError.Storage("disk full"))
        val vm = createViewModel()
        vm.exportJson(Uri.parse("content://fake/json"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isError)
        assertEquals("Export failed: disk full", vm.uiState.value.message)

        vm.clearMessage()
        assertNull(vm.uiState.value.message)
        assertFalse(vm.uiState.value.isError)
    }

    @Test
    fun passphrase_array_passed_to_service_is_the_one_given_and_is_zeroed() = runTest(dispatcher) {
        val vm = createViewModel()
        val inputPassphrase = "mysecretpassphrase".toCharArray()
        vm.createBackup(Uri.parse("content://fake/backup"), inputPassphrase)
        advanceUntilIdle()

        assertSame(inputPassphrase, backup.capturedPassphrase)
        assertTrue(inputPassphrase.all { it == '\u0000' })
    }

    @Test
    fun operations_run_off_the_main_dispatcher_on_work_context() = runTest(dispatcher) {
        val workDispatcher = StandardTestDispatcher(testScheduler)
        val vm = createViewModel(workContext = workDispatcher)

        vm.exportCsv(ExportRange.LAST_7_DAYS, Uri.parse("content://fake/csv"))
        advanceUntilIdle()
        assertSame(workDispatcher, export.lastInterceptor)
        assertFalse(export.lastInterceptor == dispatcher)

        vm.exportJson(Uri.parse("content://fake/json"))
        advanceUntilIdle()
        assertSame(workDispatcher, export.lastInterceptor)
        assertFalse(export.lastInterceptor == dispatcher)

        vm.createBackup(Uri.parse("content://fake/backup"), "passphrase123".toCharArray())
        advanceUntilIdle()
        assertSame(workDispatcher, backup.lastInterceptor)
        assertFalse(backup.lastInterceptor == dispatcher)

        vm.restoreBackup(Uri.parse("content://fake/backup"), "passphrase123".toCharArray())
        advanceUntilIdle()
        assertSame(workDispatcher, backup.lastInterceptor)
        assertFalse(backup.lastInterceptor == dispatcher)
    }

    private class FakeExportService : ExportService {
        var lastCsvRange: DateRange? = null
        var deferredCsv: CompletableDeferred<Outcome<Unit>>? = null
        var csvOutcome: Outcome<Unit> = Outcome.Success(Unit)
        var jsonOutcome: Outcome<Unit> = Outcome.Success(Unit)
        var exportJsonCalled = false
        var lastInterceptor: ContinuationInterceptor? = null

        override suspend fun exportCsv(range: DateRange, out: OutputStream): Outcome<Unit> {
            lastInterceptor = coroutineContext[ContinuationInterceptor]
            lastCsvRange = range
            deferredCsv?.await()
            return csvOutcome
        }

        override suspend fun exportJson(out: OutputStream): Outcome<Unit> {
            lastInterceptor = coroutineContext[ContinuationInterceptor]
            exportJsonCalled = true
            return jsonOutcome
        }
    }

    private class FakeBackupService : BackupService {
        var createBackupOutcome: Outcome<Unit> = Outcome.Success(Unit)
        var restoreBackupOutcome: Outcome<RestoreSummary> = Outcome.Success(RestoreSummary(0, 0, 0, 0))
        var capturedPassphrase: CharArray? = null
        var createBackupCalled = false
        var restoreBackupCalled = false
        var lastInterceptor: ContinuationInterceptor? = null

        override suspend fun createBackup(out: OutputStream, passphrase: CharArray): Outcome<Unit> {
            lastInterceptor = coroutineContext[ContinuationInterceptor]
            createBackupCalled = true
            capturedPassphrase = passphrase
            val res = createBackupOutcome
            passphrase.fill('\u0000')
            return res
        }

        override suspend fun restoreBackup(input: InputStream, passphrase: CharArray): Outcome<RestoreSummary> {
            lastInterceptor = coroutineContext[ContinuationInterceptor]
            restoreBackupCalled = true
            capturedPassphrase = passphrase
            val res = restoreBackupOutcome
            passphrase.fill('\u0000')
            return res
        }
    }

    private class FakeStreamOpener(
        private val outputStream: OutputStream = ByteArrayOutputStream(),
        private val inputStream: InputStream = ByteArrayInputStream(ByteArray(0)),
        private val throwOnOutput: Exception? = null,
        private val throwOnInput: Exception? = null,
    ) : StreamOpener {
        override fun output(uri: Uri): OutputStream {
            throwOnOutput?.let { throw it }
            return outputStream
        }

        override fun input(uri: Uri): InputStream {
            throwOnInput?.let { throw it }
            return inputStream
        }
    }
}
