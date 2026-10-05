package io.github.khaledbahaaeldin.emberbyte.data.android

import android.Manifest
import android.app.Application
import android.content.Context
import android.telephony.SubscriptionManager
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.data.source.SimInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSubscriptionManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidSimInfoSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val source = AndroidSimInfoSource(context)

    @Test fun without_the_phone_permission_no_sims_are_listed() {
        assertTrue(source.activeSims().isEmpty())
    }

    @Test fun the_default_data_subscription_needs_no_permission_and_is_minus_one_when_unknown() {
        assertTrue(source.defaultDataSubscriptionId() >= -1)
    }

    @Test fun with_the_permission_the_sims_are_listed_in_slot_order() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_PHONE_STATE)
        val shadow = shadowOf(context.getSystemService(SubscriptionManager::class.java))
        shadow.setActiveSubscriptionInfoList(
            listOf(
                ShadowSubscriptionManager.SubscriptionInfoBuilder.newBuilder().setId(5).setSimSlotIndex(1).setCarrierName("Beta").buildSubscriptionInfo(),
                ShadowSubscriptionManager.SubscriptionInfoBuilder.newBuilder().setId(3).setSimSlotIndex(0).setCarrierName("Alpha").buildSubscriptionInfo(),
            ),
        )
        assertEquals(listOf(3, 5), source.activeSims().map { it.subscriptionId })
    }
}
