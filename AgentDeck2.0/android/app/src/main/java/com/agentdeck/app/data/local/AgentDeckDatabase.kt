package com.agentdeck.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, AppSettingsEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AgentDeckDatabase : RoomDatabase() {
    abstract fun dao(): AgentDeckDao

    companion object {
        private const val NAME = "agentdeck.db"

        @Volatile
        private var instance: AgentDeckDatabase? = null

        fun get(context: Context): AgentDeckDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentDeckDatabase::class.java,
                    NAME
                ).build().also { instance = it }
            }
    }
}
