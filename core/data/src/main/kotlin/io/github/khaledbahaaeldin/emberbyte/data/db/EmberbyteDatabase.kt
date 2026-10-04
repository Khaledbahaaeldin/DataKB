package io.github.khaledbahaaeldin.emberbyte.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TotalMinuteEntity::class,
        UsageHourlyEntity::class,
        AppMetaEntity::class,
        CoverageGapEntity::class,
        CounterCheckpointEntity::class,
        PlanEntity::class,
        AddOnEntity::class,
        FreeRuleEntity::class,
        AlertRuleEntity::class,
        AlertFiredEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class EmberbyteDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun planDao(): PlanDao
}

