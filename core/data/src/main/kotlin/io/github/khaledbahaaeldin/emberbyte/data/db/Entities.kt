package io.github.khaledbahaaeldin.emberbyte.data.db

import androidx.room.Entity
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
