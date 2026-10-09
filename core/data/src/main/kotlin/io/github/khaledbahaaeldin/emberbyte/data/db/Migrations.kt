package io.github.khaledbahaaeldin.emberbyte.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `plan` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `subscriptionId` INTEGER, `capBytes` INTEGER NOT NULL, `cycleJson` TEXT NOT NULL, `rolloverJson` TEXT NOT NULL, `archived` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `add_on` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER NOT NULL, `label` TEXT NOT NULL, `bytes` INTEGER NOT NULL, `validFrom` INTEGER NOT NULL, `validUntil` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_add_on_planId` ON `add_on` (`planId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `free_rule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER NOT NULL, `label` TEXT NOT NULL, `daysMask` INTEGER NOT NULL, `startMinute` INTEGER NOT NULL, `endMinute` INTEGER NOT NULL, `packageName` TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_free_rule_planId` ON `free_rule` (`planId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `alert_rule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER NOT NULL, `thresholdPercent` INTEGER NOT NULL, `enabled` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_alert_rule_planId` ON `alert_rule` (`planId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `alert_fired` (`planId` INTEGER NOT NULL, `windowStart` INTEGER NOT NULL, `percent` INTEGER NOT NULL, PRIMARY KEY(`planId`, `windowStart`))")
    }
}

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `app_budget` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packageName` TEXT NOT NULL, `limitBytes` INTEGER NOT NULL, `period` TEXT NOT NULL, `planId` INTEGER, `warnAtPercent` INTEGER NOT NULL, `enabled` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_budget_packageName` ON `app_budget` (`packageName`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `spike_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packageName` TEXT NOT NULL, `windowStart` INTEGER NOT NULL, `detectedAt` INTEGER NOT NULL, `windowMinutes` INTEGER NOT NULL, `bytes` INTEGER NOT NULL, `baselineBytes` INTEGER NOT NULL, `multiple` REAL NOT NULL, `dismissed` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_spike_event_packageName_windowStart` ON `spike_event` (`packageName`, `windowStart`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_spike_event_detectedAt` ON `spike_event` (`detectedAt`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `budget_state` (`budgetId` INTEGER NOT NULL, `periodStart` INTEGER NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`budgetId`, `periodStart`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `spike_notice` (`packageName` TEXT NOT NULL, `notifiedAt` INTEGER NOT NULL, PRIMARY KEY(`packageName`))")
    }
}
