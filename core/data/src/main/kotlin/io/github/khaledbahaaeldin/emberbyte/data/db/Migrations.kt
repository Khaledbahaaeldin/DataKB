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
