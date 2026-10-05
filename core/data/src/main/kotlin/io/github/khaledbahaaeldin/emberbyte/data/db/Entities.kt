package io.github.khaledbahaaeldin.emberbyte.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "total_minute", primaryKeys = ["minuteStart", "network", "subscriptionId"])
data class TotalMinuteEntity(
    val minuteStart: Long,
    val network: Int,
    val subscriptionId: Int,
    val rxBytes: Long,
    val txBytes: Long,
)

@Entity(tableName = "usage_hourly", primaryKeys = ["hourStart", "uid", "network", "subscriptionId"])
data class UsageHourlyEntity(
    val hourStart: Long,
    val uid: Int,
    val network: Int,
    val subscriptionId: Int,
    val rxBytes: Long,
    val txBytes: Long,
)

@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val uid: Int,
    val packageName: String,
    val label: String,
    val updatedAt: Long,
)

@Entity(tableName = "coverage_gap")
data class CoverageGapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromAt: Long,
    val toAt: Long,
    val reason: String,
)

@Entity(tableName = "counter_checkpoint")
data class CounterCheckpointEntity(
    @PrimaryKey val source: String,
    val at: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val bootId: String,
)

@Entity(tableName = "plan")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subscriptionId: Int?,
    val capBytes: Long,
    val cycleJson: String,
    val rolloverJson: String,
    val archived: Boolean,
)

@Entity(tableName = "add_on", indices = [Index("planId")])
data class AddOnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val label: String,
    val bytes: Long,
    val validFrom: Long,
    val validUntil: Long,
)

@Entity(tableName = "free_rule", indices = [Index("planId")])
data class FreeRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val label: String,
    val daysMask: Int,       // bit 0 = Monday ... bit 6 = Sunday
    val startMinute: Int,    // minutes after midnight
    val endMinute: Int,
    val packageName: String?,
)

@Entity(tableName = "alert_rule", indices = [Index("planId")])
data class AlertRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val thresholdPercent: Int,
    val enabled: Boolean,
)

@Entity(tableName = "alert_fired", primaryKeys = ["planId", "windowStart"])
data class AlertFiredEntity(val planId: Long, val windowStart: Long, val percent: Int)

