package com.agentdeck.app.data.local

import org.json.JSONArray
import org.json.JSONObject

internal data class BackupPayload(
    val tasks: List<TaskEntity>,
    val settings: AppSettingsEntity,
    val skippedTasks: Int
)

/**
 * Pure JSON encoding for portable workspace backups. Kept free of Android
 * types so the format can be unit-tested without Room.
 */
internal object WorkspaceBackup {

    const val FORMAT = "agentdeck-workspace"
    const val VERSION = 1

    fun encode(
        tasks: List<TaskEntity>,
        settings: AppSettingsEntity?,
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt)
            .put(
                "tasks",
                JSONArray().also { array ->
                    tasks.forEach { entity ->
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
                }
            )
        settings?.let {
            root.put(
                "settings",
                JSONObject()
                    .put("activeProjectId", it.activeProjectId)
                    .put("defaultProjectId", it.defaultProjectId)
                    .put("defaultModelId", it.defaultModelId)
                    .put("demoNoticeDismissed", it.demoNoticeDismissed)
            )
        }
        return root.toString(2)
    }

    fun decode(text: String, now: Long = System.currentTimeMillis()): BackupPayload {
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: throw IllegalArgumentException("That file is not a valid AgentDeck backup.")
        if (root.optString("format") != FORMAT) {
            throw IllegalArgumentException("That file is not an AgentDeck backup.")
        }
        if (root.optInt("version", 1) > VERSION) {
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
                createdAt = item.optLong("createdAt", now),
                updatedAt = item.optLong("updatedAt", now),
                payloadJson = item.optString("payloadJson", "{}")
            )
        }
        if (entities.isEmpty() && tasksJson.length() > 0) {
            throw IllegalArgumentException("No readable tasks were found in this backup.")
        }
        return BackupPayload(entities, settings, skipped)
    }
}
