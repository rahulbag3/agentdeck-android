package com.agentdeck.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class AgentDeckDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    abstract suspend fun loadTasks(): List<TaskEntity>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    abstract suspend fun loadSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSettings(settings: AppSettingsEntity)

    @Query("DELETE FROM tasks")
    abstract suspend fun deleteTasks()

    @Query("DELETE FROM app_settings")
    abstract suspend fun deleteSettings()

    /**
     * Replace the stored workspace atomically. The in-memory state is the
     * source of truth, so a full replacement is simpler and safer than trying
     * to diff collections on every save.
     */
    @Transaction
    open suspend fun replaceAll(tasks: List<TaskEntity>, settings: AppSettingsEntity) {
        deleteTasks()
        insertTasks(tasks)
        insertSettings(settings)
    }

    @Transaction
    open suspend fun clearAll() {
        deleteTasks()
        deleteSettings()
    }
}
