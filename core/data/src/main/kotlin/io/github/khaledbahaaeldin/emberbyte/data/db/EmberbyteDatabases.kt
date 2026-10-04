package io.github.khaledbahaaeldin.emberbyte.data.db

import android.content.Context
import androidx.room.Room

/** The one Room instance of the process (two instances on one file are not allowed). */
object EmberbyteDatabases {
    @Volatile private var instance: EmberbyteDatabase? = null

    fun get(context: Context): EmberbyteDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(context.applicationContext, EmberbyteDatabase::class.java, "emberbyte.db")
            .addMigrations(MIGRATION_1_2)
            .build()
            .also { instance = it }
    }
}
