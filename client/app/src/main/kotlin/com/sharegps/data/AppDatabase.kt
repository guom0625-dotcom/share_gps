package com.sharegps.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LocationQueueEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationQueueDao(): LocationQueueDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE location_queue ADD COLUMN battery INTEGER")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "sharegps.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
