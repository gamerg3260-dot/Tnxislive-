package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.antitheft.AntiTheftDao
import com.example.antitheft.AntiTheftSettingsEntity
import com.example.antitheft.IntruderLogEntity

@Database(
    entities = [
        CommandHistoryEntity::class,
        UserMemoryEntity::class,
        ReminderEntity::class,
        IntruderLogEntity::class,
        AntiTheftSettingsEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MaxDatabase : RoomDatabase() {
    abstract fun maxDao(): MaxDao
    abstract fun reminderDao(): ReminderDao
    abstract fun antiTheftDao(): AntiTheftDao

    companion object {
        @Volatile
        private var INSTANCE: MaxDatabase? = null

        fun getInstance(context: Context): MaxDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MaxDatabase::class.java,
                    "max_assistant.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
