package com.agentdeck.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceBackupTest {

    private val settings = AppSettingsEntity(
        activeProjectId = "mobile-ui",
        defaultProjectId = "docs-site",
        defaultModelId = "qwen-1.5b",
        demoNoticeDismissed = true
    )

    private fun taskEntity(id: String) = TaskEntity(
        id = id,
        title = "Task $id",
        prompt = "Do the thing.",
        projectId = "orbit-api",
        branch = "feat/$id",
        modelId = "cloud-byok",
        status = "NEEDS_INPUT",
        createdAt = 10,
        updatedAt = 20,
        payloadJson = """{"messages":[]}"""
    )

    @Test
    fun `encode then decode preserves tasks and settings`() {
        val tasks = listOf(taskEntity("t-1"), taskEntity("t-2"))

        val decoded = WorkspaceBackup.decode(WorkspaceBackup.encode(tasks, settings))

        assertEquals(tasks, decoded.tasks)
        assertEquals(settings, decoded.settings)
        assertEquals(0, decoded.skippedTasks)
    }

    @Test
    fun `exported json carries format and version`() {
        val json = WorkspaceBackup.encode(listOf(taskEntity("t-1")), settings, exportedAt = 1234)
        assertTrue(json.contains("\"format\": \"agentdeck-workspace\""))
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("\"exportedAt\": 1234"))
    }

    @Test
    fun `non backup json is rejected`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            WorkspaceBackup.decode("""{"hello":"world"}""")
        }
        assertTrue(error.message!!.contains("not an AgentDeck backup"))
    }

    @Test
    fun `malformed json is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceBackup.decode("not json")
        }
    }

    @Test
    fun `newer backup version is rejected`() {
        val json = """{"format":"agentdeck-workspace","version":2,"settings":{},"tasks":[]}"""
        val error = assertThrows(IllegalArgumentException::class.java) {
            WorkspaceBackup.decode(json)
        }
        assertTrue(error.message!!.contains("newer version"))
    }

    @Test
    fun `missing settings is rejected`() {
        val json = """{"format":"agentdeck-workspace","version":1,"tasks":[]}"""
        val error = assertThrows(IllegalArgumentException::class.java) {
            WorkspaceBackup.decode(json)
        }
        assertTrue(error.message!!.contains("missing workspace settings"))
    }

    @Test
    fun `rows without ids are skipped`() {
        val json = """
            {
              "format": "agentdeck-workspace",
              "version": 1,
              "settings": {"activeProjectId": "orbit-api", "defaultProjectId": "orbit-api", "defaultModelId": "host-claude"},
              "tasks": [
                {"id": "t-1", "title": "Keep", "payloadJson": "{}"},
                {"title": "Drop me"},
                "not an object"
              ]
            }
        """.trimIndent()

        val decoded = WorkspaceBackup.decode(json, now = 777)

        assertEquals(1, decoded.tasks.size)
        assertEquals("t-1", decoded.tasks.first().id)
        assertEquals(2, decoded.skippedTasks)
        assertEquals(777, decoded.tasks.first().createdAt)
        assertEquals(777, decoded.tasks.first().updatedAt)
    }

    @Test
    fun `backup with no readable tasks fails`() {
        val json = """
            {
              "format": "agentdeck-workspace",
              "version": 1,
              "settings": {"activeProjectId": "orbit-api"},
              "tasks": [{"title": "no id"}]
            }
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            WorkspaceBackup.decode(json)
        }
        assertTrue(error.message!!.contains("No readable tasks"))
    }

    @Test
    fun `empty task list decodes with settings`() {
        val decoded = WorkspaceBackup.decode(WorkspaceBackup.encode(emptyList(), settings))
        assertEquals(0, decoded.tasks.size)
        assertEquals(1, decoded.settings.id)
        assertEquals("mobile-ui", decoded.settings.activeProjectId)
    }
}
