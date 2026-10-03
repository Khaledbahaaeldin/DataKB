package io.github.khaledbahaaeldin.emberbyte.data.android

import android.telephony.SubscriptionManager
import io.github.khaledbahaaeldin.emberbyte.data.source.SubscriptionSource

class TelephonySubscriptionSource : SubscriptionSource {
    override fun defaultDataSubscriptionId(): Int = try {
        SubscriptionManager.getDefaultDataSubscriptionId().let { if (it < 0) -1 else it }
    } catch (_: RuntimeException) {
        -1
    }
}
