package com.agentdeck.app.data.local

import android.content.Context
import com.agentdeck.app.data.AgentDeckData
import com.agentdeck.app.data.AppSettings
import com.agentdeck.app.data.Task
import org.json.JSONArray
import org.json.JSONObject

/** Everything restored from disk, plus diagnostics for unreadable rows. */
data class StoredWorkspace(
    val tasks: List<Task>,
    val settings: AppSettings?,
    val skippedTasks: Int
)

data class ImportResult(val importedTasks: Int, val skippedTasks: Int)

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

    /**
     * Portable JSON backup. Contains conversation history and repository
     * snapshots, never API keys or tokens (those live in the Keystore).
     */
    suspend fun exportJson(): String {
        val root = JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("version", BACKUP_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("tasks", JSONArray().also { array ->
                dao.loadTasks().forEach { entity ->
                    array.put(
                        JSONObject()
                            .put("id", entity.id)
                            .put("title", entity.title)
                            .put("prompt", entity.prompt)
                            .put("projectId", entity.projectId)
                            .put("branch", entity.branch)
                            .put("modelId", entity.modelId)
                            .put("status", entity.status)
                            .put("createdAt", entity.createdAt)
                            .put("updatedAt", entity.updatedAt)
                            .put("payloadJson", entity.payloadJson)
                    )
                }
            })
        dao.loadSettings()?.let { settings ->
            root.put(
                "settings",
                JSONObject()
                    .put("activeProjectId", settings.activeProjectId)
                    .put("defaultProjectId", settings.defaultProjectId)
                    .put("defaultModelId", settings.defaultModelId)
                    .put("demoNoticeDismissed", settings.demoNoticeDismissed)
            )
        }
        return root.toString(2)
    }

    /** Replaces the stored workspace with a backup. Throws on invalid input. */
    suspend fun importJson(text: String): ImportResult {
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: throw IllegalArgumentException("That file is not a valid AgentDeck backup.")
        if (root.optString("format") != BACKUP_FORMAT) {
            throw IllegalArgumentException("That file is not an AgentDeck backup.")
        }
        if (root.optInt("version", 1) > BACKUP_VERSION) {
            throw IllegalArgumentException("This backup was created by a newer version of AgentDeck.")
        }
        val settingsJson = root.optJSONObject("settings")
            ?: throw IllegalArgumentException("The backup is missing workspace settings.")
        val settings = AppSettingsEntity(
            id = 1,
            activeProjectId = settingsJson.optString("activeProjectId", "orbit-api"),
            defaultProjectId = settingsJson.optString("defaultProjectId", "orbit-api"),
            defaultModelId = settingsJson.optString("defaultModelId", "host-claude"),
            demoNoticeDismissed = settingsJson.optBoolean("demoNoticeDismissed", false)
        )

        val tasksJson = root.optJSONArray("tasks") ?: JSONArray()
        val entities = ArrayList<TaskEntity>(tasksJson.length())
        var skipped = 0
        for (index in 0 until tasksJson.length()) {
            val item = tasksJson.optJSONObject(index)
            if (item == null) {
                skipped++
                continue
            }
            val id = item.optString("id")
            if (id.isBlank()) {
                skipped++
                continue
            }
            entities += TaskEntity(
                id = id,
                title = item.optString("title", "Untitled task"),
                prompt = item.optString("prompt"),
                projectId = item.optString("projectId", settings.activeProjectId),
                branch = item.optString("branch"),
                modelId = item.optString("modelId", settings.defaultModelId),
                status = item.optString("status", "NEEDS_INPUT"),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                payloadJson = item.optString("payloadJson", "{}")
            )
        }
        if (entities.isEmpty() && tasksJson.length() > 0) {
            throw IllegalArgumentException("No readable tasks were found in this backup.")
        }
        dao.replaceAll(entities, settings)
        return ImportResult(importedTasks = entities.size, skippedTasks = skipped)
    }

    private companion object {
        const val BACKUP_FORMAT = "agentdeck-workspace"
        const val BACKUP_VERSION = 1
    }
}
