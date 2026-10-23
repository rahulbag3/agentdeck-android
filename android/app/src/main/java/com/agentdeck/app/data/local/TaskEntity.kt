package com.agentdeck.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One task row. Scalar columns stay queryable; the conversation record
 * (messages, activity, files, checks, decisions, plan, repository snapshot)
 * is stored in [payloadJson] because it is only ever loaded as a whole.
 */
@Entity(tableName = "tasks", indices = [Index("updatedAt")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val prompt: String,
    val projectId: String,
    val branch: String,
    val modelId: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val payloadJson: String
)

/** Single-row table for non-secret workspace settings. */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val activeProjectId: String,
    val defaultProjectId: String,
    val defaultModelId: String,
    val demoNoticeDismissed: Boolean
)
