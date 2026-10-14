package io.github.khaledbahaaeldin.emberbyte.sampler

import android.app.Application
import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.R
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.CycleWindow
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.PlanState
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover
import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.model.FrameworkMethod
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.internal.bytecode.InstrumentationConfiguration
import org.robolectric.internal.bytecode.Interceptor
import org.robolectric.internal.bytecode.MethodRef
import org.robolectric.internal.bytecode.MethodSignature
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNotification
import org.robolectric.util.Function

@RunWith(LiveNotificationRobolectricTestRunner::class)
@Config(
    sdk = [35],
    shadows = [
        ShadowProgressStyle::class,
        ShadowProgressStyleSegment::class,
        ShadowLiveNotificationBuilder::class,
        ShadowLiveNotification::class,
    ],
)
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
    fun live_update_at_sdk_36_has_progress_style_and_promoted_ongoing() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val text = liveNotificationText(
            todayBytes = 1_240_000_000L,
            rxBps = 4_200_000L,
            units = ByteUnits.DECIMAL,
            showSpeed = true,
            remainingBytes = 3_200_000_000L,
            progress = LiveProgress(0.63f, "3.2 GB"),
        )
        val notification = LiveNotificationBuilder(app, sdkInt = 36).build(text)

        assertEquals("android.app.Notification\$ProgressStyle", notification.extras.getString(Notification.EXTRA_TEMPLATE))
        assertEquals(630, notification.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals(1000, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(1000, notification.extras.getInt("android.progressSegments"))
        val shortCritical = try {
            notification.shortCriticalText
        } catch (_: NoSuchMethodError) {
            notification.extras.getCharSequence("android.shortCriticalText")?.toString()
        }
        assertEquals("3.2 GB", shortCritical)

        val isPromoted = try {
            notification.isRequestPromotedOngoing
        } catch (_: NoSuchMethodError) {
            notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING)
        }
        assertTrue(isPromoted)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(NotificationChannels.LIVE, notification.channelId)
        assertNull(notification.contentView)
        assertNull(notification.bigContentView)
        assertNull(notification.headsUpContentView)
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_COLORIZED, false))
        assertEquals(R.drawable.ic_stat_ember, notification.smallIcon.resId)
        assertEquals("emberbyte://home", shadowOf(notification.contentIntent).savedIntent.data.toString())
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

@Implements(Notification.ProgressStyle::class)
class ShadowProgressStyle {
    @RealObject
    private lateinit var realStyle: Notification.ProgressStyle

    @get:Implementation
    var progress: Int = 0

    val segments = mutableListOf<Notification.ProgressStyle.Segment>()

    @Implementation
    protected fun __constructor__() {}

    @Implementation
    fun setProgress(progress: Int): Notification.ProgressStyle {
        this.progress = progress
        return realStyle
    }

    @Implementation
    fun addProgressSegment(segment: Notification.ProgressStyle.Segment): Notification.ProgressStyle {
        this.segments.add(segment)
        return realStyle
    }

    @Implementation
    fun getProgressSegments(): List<Notification.ProgressStyle.Segment> = segments.toList()

    @Implementation
    fun getProgressMax(): Int = segments.sumOf { (Shadow.extract<ShadowProgressStyleSegment>(it)).length }
}

@Implements(Notification.ProgressStyle.Segment::class)
class ShadowProgressStyleSegment {
    @get:Implementation
    var length: Int = 0

    @Implementation
    protected fun __constructor__(length: Int) {
        this.length = length
    }
}

@Implements(Notification::class)
class ShadowLiveNotification : ShadowNotification() {
    @RealObject
    private lateinit var realNotification: Notification

    @get:Implementation
    val shortCriticalText: String?
        get() = realNotification.extras.getCharSequence("android.shortCriticalText")?.toString()

    @get:Implementation
    val isRequestPromotedOngoing: Boolean
        get() = realNotification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, false)
}

@Implements(Notification.Builder::class)
class ShadowLiveNotificationBuilder {
    @RealObject
    private lateinit var realBuilder: Notification.Builder

    private var style: Notification.Style? = null

    @Implementation
    fun setStyle(style: Notification.Style): Notification.Builder {
        this.style = style
        return realBuilder
    }

    @Implementation
    fun build(): Notification {
        val notification: Notification = Shadow.directlyOn(realBuilder, Notification.Builder::class.java, "build")
        notification.extras.putAll(realBuilder.extras)
        if (style != null) {
            notification.extras.putString(Notification.EXTRA_TEMPLATE, style!!::class.java.name)
            if (style is Notification.ProgressStyle) {
                val shadowStyle = Shadow.extract<ShadowProgressStyle>(style)
                notification.extras.putInt(Notification.EXTRA_PROGRESS, shadowStyle.progress)
                val max = shadowStyle.getProgressMax()
                notification.extras.putInt(Notification.EXTRA_PROGRESS_MAX, max)
                notification.extras.putInt("android.progressSegments", max)
            }
        }
        return notification
    }
}

class LiveNotificationRobolectricTestRunner(testClass: Class<*>) : RobolectricTestRunner(testClass) {
    override fun findInterceptors(): Collection<Interceptor> {
        val list = ArrayList(super.findInterceptors())
        list.add(Api36NotificationInterceptor())
        return list
    }

    override fun createClassLoaderConfig(method: FrameworkMethod): InstrumentationConfiguration {
        val builder = InstrumentationConfiguration.Builder(super.createClassLoaderConfig(method))
        builder.addInstrumentedClass("io.github.khaledbahaaeldin.emberbyte.sampler.LiveNotificationBuilder")
        builder.addInstrumentedClass("io.github.khaledbahaaeldin.emberbyte.sampler.LiveNotificationTest")
        return builder.build()
    }
}

class Api36NotificationInterceptor : Interceptor(
    MethodRef("android.app.Notification\$Builder", "setShortCriticalText"),
    MethodRef("android.app.Notification\$Builder", "setRequestPromotedOngoing"),
    MethodRef("android.app.Notification", "getShortCriticalText"),
    MethodRef("android.app.Notification", "isRequestPromotedOngoing"),
) {
    override fun handle(methodSignature: MethodSignature): Function<Any, Any> {
        return Function { _, value, params ->
            when (methodSignature.methodName) {
                "setShortCriticalText" -> NotificationApi36InterceptionBridge.setShortCriticalText(value, params?.getOrNull(0))
                "setRequestPromotedOngoing" -> NotificationApi36InterceptionBridge.setRequestPromotedOngoing(value, params?.getOrNull(0) as? Boolean ?: true)
                "getShortCriticalText" -> NotificationApi36InterceptionBridge.getShortCriticalText(value)
                "isRequestPromotedOngoing" -> NotificationApi36InterceptionBridge.isRequestPromotedOngoing(value)
                else -> null
            }
        }
    }

    override fun getMethodHandle(methodName: String, type: MethodType): MethodHandle {
        val lookup = MethodHandles.lookup()
        val bridgeClass = Class.forName(
            "io.github.khaledbahaaeldin.emberbyte.sampler.NotificationApi36InterceptionBridge",
            true,
            Thread.currentThread().contextClassLoader,
        )
        val handle = when (methodName) {
            "setShortCriticalText" -> lookup.findStatic(
                bridgeClass,
                "setShortCriticalText",
                MethodType.methodType(Any::class.java, Any::class.java, Any::class.java),
            )
            "setRequestPromotedOngoing" -> lookup.findStatic(
                bridgeClass,
                "setRequestPromotedOngoing",
                MethodType.methodType(Any::class.java, Any::class.java, Boolean::class.javaPrimitiveType),
            )
            "getShortCriticalText" -> lookup.findStatic(
                bridgeClass,
                "getShortCriticalText",
                MethodType.methodType(String::class.java, Any::class.java),
            )
            "isRequestPromotedOngoing" -> lookup.findStatic(
                bridgeClass,
                "isRequestPromotedOngoing",
                MethodType.methodType(Boolean::class.javaPrimitiveType, Any::class.java),
            )
            else -> throw NoSuchMethodException(methodName)
        }
        return handle.asType(type)
    }
}

object NotificationApi36InterceptionBridge {
    @JvmStatic
    fun setShortCriticalText(builder: Any, text: Any?): Any {
        if (builder is Notification.Builder) {
            builder.extras.putCharSequence("android.shortCriticalText", text as? CharSequence)
        }
        return builder
    }

    @JvmStatic
    fun setRequestPromotedOngoing(builder: Any, request: Boolean): Any {
        if (builder is Notification.Builder) {
            builder.extras.putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, request)
        }
        return builder
    }

    @JvmStatic
    fun getShortCriticalText(notification: Any): String? {
        return if (notification is Notification) {
            notification.extras.getCharSequence("android.shortCriticalText")?.toString()
        } else null
    }

    @JvmStatic
    fun isRequestPromotedOngoing(notification: Any): Boolean {
        return if (notification is Notification) {
            notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, false)
        } else false
    }
}
