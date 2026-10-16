package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.app.Notification
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.R
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveNotificationTest {
    @Test fun text_shows_today_and_speed() {
        val text = liveNotificationText(1_240_000_000L, 4_200_000L, ByteUnits.DECIMAL, showSpeed = true)
        assertEquals("1.24 GB today", text.title)
        assertEquals("↓ 4.20 MB/s", text.text)
    }

    @Test fun text_without_speed_has_no_body() {
        val text = liveNotificationText(512L, 9_999L, ByteUnits.DECIMAL, showSpeed = false)
        assertEquals("512 B today", text.title)
        assertNull(text.text)
    }

    @Test fun zero_usage_reads_naturally() {
        val text = liveNotificationText(0L, 0L, ByteUnits.DECIMAL, showSpeed = true)
        assertEquals("0 B today", text.title)
        assertEquals("↓ 0 B/s", text.text)
    }

    @Test fun binary_units_are_respected() {
        assertEquals("1.00 MiB today", liveNotificationText(1_048_576L, 0L, ByteUnits.BINARY, showSpeed = false).title)
    }

    @Test fun offline_says_no_connection_instead_of_a_stale_speed() {
        val text = liveNotificationText(1_000_000L, 195L, ByteUnits.DECIMAL, showSpeed = true, offline = true)
        assertEquals("1.00 MB today", text.title)
        assertEquals("No connection", text.text)
    }

    @Test fun the_built_notification_is_ongoing_on_the_live_channel() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val notification = LiveNotificationBuilder(app).build(LiveNotificationText("1.24 GB today", "↓ 4.20 MB/s"))
        assertEquals(NotificationChannels.LIVE, notification.channelId)
        assertEquals("1.24 GB today", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("↓ 4.20 MB/s", notification.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("emberbyte://home", shadowOf(notification.contentIntent).savedIntent.data.toString())
    }

    @Test fun remaining_data_comes_first_and_the_speed_follows() {
        val t = liveNotificationText(1_240_000_000L, 4_200_000L, ByteUnits.DECIMAL, showSpeed = true, remainingBytes = 3_800_000_000L)
        assertEquals("1.24 GB today", t.title)
        assertEquals("3.80 GB left · ↓ 4.20 MB/s", t.text)
    }

    @Test fun remaining_data_alone_when_the_speed_is_hidden() {
        val t = liveNotificationText(1_000_000L, 0, ByteUnits.DECIMAL, showSpeed = false, remainingBytes = 3_800_000_000L)
        assertEquals("3.80 GB left", t.text)
    }

    @Test fun offline_keeps_the_remaining_data() {
        val t = liveNotificationText(0, 0, ByteUnits.DECIMAL, showSpeed = true, offline = true, remainingBytes = 3_800_000_000L)
        assertEquals("3.80 GB left · No connection", t.text)
    }

    @Test fun no_plan_keeps_the_old_text() {
        val t = liveNotificationText(1_000_000L, 4_200_000L, ByteUnits.DECIMAL, showSpeed = true)
        assertEquals("↓ 4.20 MB/s", t.text)
        assertNull(liveNotificationText(1_000_000L, 0, ByteUnits.DECIMAL, showSpeed = false).text)
    }

    @Test fun nothing_left_says_so() {
        val t = liveNotificationText(1, 0, ByteUnits.DECIMAL, showSpeed = false, remainingBytes = 0L)
        assertEquals("No data left", t.text)
    }

    @Test fun text_with_progress_carries_progress_and_null_is_identical_to_existing() {
        val progress = LiveProgress(0.63f, "3.2 GB")
        val withProgress = liveNotificationText(
            todayBytes = 1_240_000_000L,
            rxBps = 4_200_000L,
            units = ByteUnits.DECIMAL,
            showSpeed = true,
            remainingBytes = 3_800_000_000L,
            progress = progress,
        )
        assertEquals(progress, withProgress.progress)
        assertEquals("1.24 GB today", withProgress.title)
        assertEquals("3.80 GB left · ↓ 4.20 MB/s", withProgress.text)

        val withoutProgress = liveNotificationText(
            todayBytes = 1_240_000_000L,
            rxBps = 4_200_000L,
            units = ByteUnits.DECIMAL,
            showSpeed = true,
            remainingBytes = 3_800_000_000L,
            progress = null,
        )
        assertNull(withoutProgress.progress)
        assertEquals(withProgress.title, withoutProgress.title)
        assertEquals(withProgress.text, withoutProgress.text)
        assertEquals(withProgress.contentDescription, withoutProgress.contentDescription)
    }

    @Test
    fun resolve_live_notification_style_table() {
        // sdk < 36 -> COMPAT
        assertEquals(LiveNotificationStyle.COMPAT, resolveLiveNotificationStyle(29, 0, true))
        assertEquals(LiveNotificationStyle.COMPAT, resolveLiveNotificationStyle(35, 0, true))

        // sdk 36.0 (sdkInt = 36, sdkIntFull < BAKLAVA_1) -> PROGRESS_AND_CHIP
        assertEquals(LiveNotificationStyle.PROGRESS_AND_CHIP, resolveLiveNotificationStyle(36, 0, true))
        assertEquals(LiveNotificationStyle.PROGRESS_AND_CHIP, resolveLiveNotificationStyle(36, Build.VERSION_CODES_FULL.BAKLAVA_1 - 1, true))

        // sdk 36.1 (sdkInt = 36, sdkIntFull >= BAKLAVA_1) -> PROMOTED
        assertEquals(LiveNotificationStyle.PROMOTED, resolveLiveNotificationStyle(36, Build.VERSION_CODES_FULL.BAKLAVA_1, true))

        // sdk 37 -> PROMOTED
        assertEquals(LiveNotificationStyle.PROMOTED, resolveLiveNotificationStyle(37, 0, true))
        assertEquals(LiveNotificationStyle.PROMOTED, resolveLiveNotificationStyle(37, Build.VERSION_CODES_FULL.BAKLAVA_1, true))

        // no progress -> always COMPAT
        assertEquals(LiveNotificationStyle.COMPAT, resolveLiveNotificationStyle(29, 0, false))
        assertEquals(LiveNotificationStyle.COMPAT, resolveLiveNotificationStyle(35, 0, false))
        assertEquals(LiveNotificationStyle.COMPAT, resolveLiveNotificationStyle(36, 0, false))
        assertEquals(LiveNotificationStyle.COMPAT, resolveLiveNotificationStyle(36, Build.VERSION_CODES_FULL.BAKLAVA_1, false))
        assertEquals(LiveNotificationStyle.COMPAT, resolveLiveNotificationStyle(37, 0, false))
    }

    @Test
    fun recording_seam_proves_promoted_call_never_made_at_36_0() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val text = liveNotificationText(
            todayBytes = 1_240_000_000L,
            rxBps = 4_200_000L,
            units = ByteUnits.DECIMAL,
            showSpeed = true,
            remainingBytes = 3_200_000_000L,
            progress = LiveProgress(0.63f, "3.2 GB"),
        )
        val recorder = RecordingPlatformNotificationCaller()
        val builder = LiveNotificationBuilder(
            context = app,
            sdkInt = 36,
            sdkIntFull = 0,
            platformCaller = recorder,
        )
        val notification = builder.build(text)

        assertEquals(1, recorder.progressCalls.size)
        assertEquals(0.63f, recorder.progressCalls[0])
        assertEquals(listOf("3.2 GB"), recorder.shortCriticalTextCalls)
        // CRITICAL PROOF: setRequestPromotedOngoing is NEVER called at 36.0!
        assertTrue(recorder.promotedOngoingCalls.isEmpty())
        assertEquals(NotificationChannels.LIVE, notification.channelId)
    }

    @Test
    fun recording_seam_proves_promoted_call_made_at_36_1_and_37() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val text = liveNotificationText(
            todayBytes = 1_240_000_000L,
            rxBps = 4_200_000L,
            units = ByteUnits.DECIMAL,
            showSpeed = true,
            remainingBytes = 3_200_000_000L,
            progress = LiveProgress(0.63f, "3.2 GB"),
        )

        val recorder361 = RecordingPlatformNotificationCaller()
        val builder361 = LiveNotificationBuilder(
            context = app,
            sdkInt = 36,
            sdkIntFull = Build.VERSION_CODES_FULL.BAKLAVA_1,
            platformCaller = recorder361,
        )
        builder361.build(text)
        assertEquals(listOf(true), recorder361.promotedOngoingCalls)

        val recorder37 = RecordingPlatformNotificationCaller()
        val builder37 = LiveNotificationBuilder(
            context = app,
            sdkInt = 37,
            sdkIntFull = 0,
            platformCaller = recorder37,
        )
        builder37.build(text)
        assertEquals(listOf(true), recorder37.promotedOngoingCalls)
    }

    @Test
    fun recording_seam_proves_platform_calls_not_made_at_sdk_35_or_when_progress_null() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val textWithProgress = liveNotificationText(
            todayBytes = 1_240_000_000L,
            rxBps = 4_200_000L,
            units = ByteUnits.DECIMAL,
            showSpeed = true,
            remainingBytes = 3_200_000_000L,
            progress = LiveProgress(0.63f, "3.2 GB"),
        )
        val recorder35 = RecordingPlatformNotificationCaller()
        val builder35 = LiveNotificationBuilder(
            context = app,
            sdkInt = 35,
            sdkIntFull = 0,
            platformCaller = recorder35,
        )
        builder35.build(textWithProgress)
        assertTrue(recorder35.progressCalls.isEmpty())
        assertTrue(recorder35.shortCriticalTextCalls.isEmpty())
        assertTrue(recorder35.promotedOngoingCalls.isEmpty())

        val textNoProgress = liveNotificationText(
            todayBytes = 1_240_000_000L,
            rxBps = 4_200_000L,
            units = ByteUnits.DECIMAL,
            showSpeed = true,
            progress = null,
        )
        val recorderNoProgress = RecordingPlatformNotificationCaller()
        val builder36NoProgress = LiveNotificationBuilder(
            context = app,
            sdkInt = 36,
            sdkIntFull = Build.VERSION_CODES_FULL.BAKLAVA_1,
            platformCaller = recorderNoProgress,
        )
        builder36NoProgress.build(textNoProgress)
        assertTrue(recorderNoProgress.progressCalls.isEmpty())
        assertTrue(recorderNoProgress.shortCriticalTextCalls.isEmpty())
        assertTrue(recorderNoProgress.promotedOngoingCalls.isEmpty())
    }

    @Test
    fun sampler_service_survives_failing_builder_with_fallback() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val service = SamplerService()
        val throwingBuilder = object : LiveNotificationBuilder(app) {
            override fun build(text: LiveNotificationText): Notification {
                throw NoSuchMethodError("setRequestPromotedOngoing")
            }
        }
        val text = LiveNotificationText("1.24 GB today", "↓ 4.20 MB/s")

        assertFalse(service.liveUpdateFailed)
        val fallbackNotification = service.buildNotificationSafely(throwingBuilder, text)
        assertTrue(service.liveUpdateFailed)
        assertEquals(NotificationChannels.LIVE, fallbackNotification.channelId)
        assertNull(fallbackNotification.extras.getString(Notification.EXTRA_TEMPLATE))

        // Subsequent calls do not throw, they return standard notification
        val secondNotification = service.buildNotificationSafely(throwingBuilder, text)
        assertEquals(NotificationChannels.LIVE, secondNotification.channelId)
    }

    @Test
    fun at_sdk_36_without_progress_uses_notification_compat() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val text = liveNotificationText(
            todayBytes = 1_240_000_000L,
            rxBps = 4_200_000L,
            units = ByteUnits.DECIMAL,
            showSpeed = true,
            progress = null,
        )
        val notification = LiveNotificationBuilder(app, sdkInt = 36).build(text)
        assertNull(notification.extras.getString(Notification.EXTRA_TEMPLATE))
    }

    @Test
    fun at_sdk_35_with_progress_uses_notification_compat() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val text = liveNotificationText(
            todayBytes = 1_240_000_000L,
            rxBps = 4_200_000L,
            units = ByteUnits.DECIMAL,
            showSpeed = true,
            progress = LiveProgress(0.63f, "3.2 GB"),
        )
        val notification = LiveNotificationBuilder(app, sdkInt = 35).build(text)
        assertNull(notification.extras.getString(Notification.EXTRA_TEMPLATE))
    }

    @Test
    fun manifest_declares_post_promoted_notifications_permission() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val info = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions?.toList().orEmpty()
        assertTrue(
            "Expected POST_PROMOTED_NOTIFICATIONS in manifest permissions $permissions",
            permissions.contains("android.permission.POST_PROMOTED_NOTIFICATIONS"),
        )
    }

    @Test
    fun chip_formats_contract_examples() {
        assertEquals("3.2 GB", formatProgressChip(3_200_000_000L, ByteUnits.DECIMAL))
        assertEquals("850 MB", formatProgressChip(850_000_000L, ByteUnits.DECIMAL))
        assertEquals("12 GB", formatProgressChip(12_000_000_000L, ByteUnits.DECIMAL))
        assertEquals("1.1 TB", formatProgressChip(1_100_000_000_000L, ByteUnits.DECIMAL))
        assertEquals("0 B", formatProgressChip(0L, ByteUnits.DECIMAL))
    }

    @Test
    fun chip_is_at_most_7_characters_over_range() {
        val multipliers = listOf(
            1L,
            1_000L,
            1_000_000L,
            1_000_000_000L,
            1_000_000_000_000L,
        )
        val binaryMultipliers = listOf(
            1L,
            1024L,
            1024L * 1024L,
            1024L * 1024L * 1024L,
            1024L * 1024L * 1024L * 1024L,
        )
        val samples = listOf(
            0.0, 0.1, 0.5, 1.0, 1.05, 3.2, 9.9, 9.99, 10.0, 12.0, 99.0, 99.9, 100.0, 850.0, 999.0, 999.9,
            1_000.0, 5_000.0, 10_000.0, 50_000.0, 100_000.0,
        )

        for (mult in multipliers) {
            for (s in samples) {
                val bytes = (s * mult).toLong()
                val chip = formatProgressChip(bytes, ByteUnits.DECIMAL)
                assertTrue("Decimal chip '$chip' for $bytes exceeds 7 chars", chip.length <= 7)
            }
        }
        for (mult in binaryMultipliers) {
            for (s in samples) {
                val bytes = (s * mult).toLong()
                val chip = formatProgressChip(bytes, ByteUnits.BINARY)
                assertTrue("Binary chip '$chip' for $bytes exceeds 7 chars", chip.length <= 7)
            }
        }
        val maxDecimalChip = formatProgressChip(Long.MAX_VALUE, ByteUnits.DECIMAL)
        assertTrue("Max decimal chip '$maxDecimalChip' exceeds 7 chars", maxDecimalChip.length <= 7)
        val maxBinaryChip = formatProgressChip(Long.MAX_VALUE, ByteUnits.BINARY)
        assertTrue("Max binary chip '$maxBinaryChip' exceeds 7 chars", maxBinaryChip.length <= 7)
    }

    @Test
    fun chip_caps_at_last_unit_for_huge_decimal_values() {
        assertEquals("999 TB", formatProgressChip(10_000L * 1_000_000_000_000L, ByteUnits.DECIMAL))
        assertEquals("999 TiB", formatProgressChip(10_000L * 1024L * 1024L * 1024L * 1024L, ByteUnits.BINARY))
    }

    @Test
    fun live_progress_of_pure_function() {
        assertNull(liveProgressOf(null, ByteUnits.DECIMAL))

        // Normal active plan
        val normal = samplePlanState(remainingBytes = 3_200_000_000L, fractionUsed = 0.63f)
        val normalProgress = liveProgressOf(normal, ByteUnits.DECIMAL)
        assertEquals(0.63f, normalProgress?.fractionUsed)
        assertEquals("3.2 GB", normalProgress?.chip)

        // Fraction clamped between 0 and 1
        val underflow = samplePlanState(remainingBytes = 3_200_000_000L, fractionUsed = -0.2f)
        assertEquals(0f, liveProgressOf(underflow, ByteUnits.DECIMAL)?.fractionUsed)

        val overflow = samplePlanState(remainingBytes = 100L, fractionUsed = 1.4f)
        assertEquals(1f, liveProgressOf(overflow, ByteUnits.DECIMAL)?.fractionUsed)

        // Quantisation kills mutant D4a
        val quant = samplePlanState(remainingBytes = 3_200_000_000L, fractionUsed = 0.634f)
        assertEquals(0.634f, liveProgressOf(quant, ByteUnits.DECIMAL)?.fractionUsed)

        // Exhausted plan gives fraction 1 and chip "0 B"
        val exhaustedZero = samplePlanState(remainingBytes = 0L, fractionUsed = 1.0f)
        val exhaustedProgress = liveProgressOf(exhaustedZero, ByteUnits.DECIMAL)
        assertEquals(1f, exhaustedProgress?.fractionUsed)
        assertEquals("0 B", exhaustedProgress?.chip)

        val exhaustedNegative = samplePlanState(remainingBytes = -500_000L, fractionUsed = 1.1f)
        val negativeProgress = liveProgressOf(exhaustedNegative, ByteUnits.DECIMAL)
        assertEquals(1f, negativeProgress?.fractionUsed)
        assertEquals("0 B", negativeProgress?.chip)

        // Estimated / approximate plan still shows progress
        val estimated = samplePlanState(remainingBytes = 3_200_000_000L, fractionUsed = 0.63f, isApproximate = true)
        val estimatedProgress = liveProgressOf(estimated, ByteUnits.DECIMAL)
        assertEquals(0.63f, estimatedProgress?.fractionUsed)
        assertEquals("3.2 GB", estimatedProgress?.chip)
    }

    private fun samplePlanState(
        remainingBytes: Long,
        fractionUsed: Float,
        isApproximate: Boolean = false,
    ) = PlanState(
        plan = Plan(
            id = 1L,
            name = "Primary",
            subscriptionId = null,
            capBytes = 10_000_000_000L,
            cycle = Cycle.MonthlyOnDay(1, LocalTime.MIDNIGHT, ZoneId.of("UTC")),
            rollover = Rollover.None,
        ),
        window = CycleWindow(Instant.EPOCH, Instant.EPOCH.plusSeconds(86400 * 30)),
        effectiveCapBytes = 10_000_000_000L,
        usedBytes = (10_000_000_000L * fractionUsed).toLong(),
        remainingBytes = remainingBytes,
        rolledOverBytes = 0L,
        addOnBytes = 0L,
        freeBytes = 0L,
        daysLeft = 10,
        fractionUsed = fractionUsed,
        isApproximate = isApproximate,
    )
}

class RecordingPlatformNotificationCaller : PlatformNotificationCaller {
    val progressCalls = mutableListOf<Float>()
    val shortCriticalTextCalls = mutableListOf<String>()
    val promotedOngoingCalls = mutableListOf<Boolean>()

    override fun setProgressStyle(builder: Notification.Builder, fractionUsed: Float): Notification.Builder {
        progressCalls.add(fractionUsed)
        return builder
    }

    override fun setShortCriticalText(builder: Notification.Builder, chip: String): Notification.Builder {
        shortCriticalTextCalls.add(chip)
        return builder
    }

    override fun setRequestPromotedOngoing(builder: Notification.Builder, request: Boolean): Notification.Builder {
        promotedOngoingCalls.add(request)
        return builder
    }
}
