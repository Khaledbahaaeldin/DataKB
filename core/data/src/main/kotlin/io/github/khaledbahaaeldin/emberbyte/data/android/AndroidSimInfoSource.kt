package io.github.khaledbahaaeldin.emberbyte.data.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import io.github.khaledbahaaeldin.emberbyte.data.source.SimInfo
import io.github.khaledbahaaeldin.emberbyte.data.source.SimInfoSource

class AndroidSimInfoSource(private val context: Context) : SimInfoSource {
    @SuppressLint("MissingPermission")
    override fun activeSims(): List<SimInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return emptyList()
        return try {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
                .map { info ->
                    SimInfo(info.subscriptionId, info.simSlotIndex, info.displayName?.toString().orEmpty().ifBlank { "SIM ${info.simSlotIndex + 1}" })
                }
                .sortedBy { it.slotIndex }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    override fun defaultDataSubscriptionId(): Int = try {
        SubscriptionManager.getDefaultDataSubscriptionId().let { if (it < 0) -1 else it }
    } catch (_: RuntimeException) {
        -1
    }
}
