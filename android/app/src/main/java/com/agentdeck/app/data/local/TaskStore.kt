package com.agentdeck.app.data.local

import android.content.Context
import com.agentdeck.app.data.AgentDeckData
import com.agentdeck.app.data.AppSettings
import com.agentdeck.app.data.Task

/** Everything restored from disk, plus diagnostics for unreadable rows. */
data class StoredWorkspace(
    val tasks: List<Task>,
    val settings: AppSettings?,
    val skippedTasks: Int
)

/**
 * Thin persistence facade over Room. Loads the whole workspace once at
 * startup and writes a consistent snapshot; the app's in-memory state remains
 * the source of truth for the UI.
 */
class TaskStore(context: Context) {
    private val dao = AgentDeckDatabase.get(context).dao()

    suspend fun load(): StoredWorkspace {
        val tasks = ArrayList<Task>()
        var skipped = 0
        for (entity in dao.loadTasks()) {
            val task = TaskCodec.fromEntity(entity)
            if (task == null) skipped++ else tasks += task
        }
        return StoredWorkspace(
            tasks = tasks,
            settings = dao.loadSettings()?.toModel(),
            skippedTasks = skipped
        )
    }

    suspend fun save(data: AgentDeckData) {
        dao.replaceAll(TaskCodec.toEntities(data.tasks), data.settings.toEntity())
    }

    suspend fun clear() {
        dao.clearAll()
    }
}
