package com.agentdeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskRebindTest {

    private val imported = ImportedModel(
        id = "local-new",
        name = "Qwen3.5-4B-Q4_K_M",
        path = "/models/local-new.gguf",
        sizeBytes = 2_740_937_888,
        importedAt = 1
    )

    @Test
    fun `orphaned local task reattaches to the model with the same name`() {
        val task = task(
            modelId = "local-old",
            messages = listOf(agentMessage("Qwen3.5-4B-Q4_K_M", ModelProvider.LOCAL))
        )

        val rebound = rebindOrphanedTask(task, setOf("local-new"), listOf(imported))

        assertEquals("local-new", rebound?.modelId)
    }

    @Test
    fun `resolved tasks are left alone`() {
        val task = task(
            modelId = "local-new",
            messages = listOf(agentMessage("Qwen3.5-4B-Q4_K_M", ModelProvider.LOCAL))
        )

        assertNull(rebindOrphanedTask(task, setOf("local-new"), listOf(imported)))
    }

    @Test
    fun `cloud tasks are never rebound to a local model`() {
        val task = task(
            modelId = "cloud-missing",
            messages = listOf(agentMessage("Qwen3.5-4B-Q4_K_M", ModelProvider.CLOUD))
        )

        assertNull(rebindOrphanedTask(task, emptySet(), listOf(imported)))
    }

    @Test
    fun `ambiguous or missing names are not guessed`() {
        val task = task(
            modelId = "local-old",
            messages = listOf(agentMessage("Qwen3.5-4B-Q4_K_M", ModelProvider.LOCAL))
        )
        val duplicate = imported.copy(id = "local-other")

        assertNull(rebindOrphanedTask(task, emptySet(), listOf(imported, duplicate)))
        assertNull(rebindOrphanedTask(task, emptySet(), emptyList()))
    }

    @Test
    fun `a gguf suffix on the historical name still matches`() {
        val task = task(
            modelId = "local-old",
            messages = listOf(agentMessage("Qwen3.5-4B-Q4_K_M.gguf", ModelProvider.LOCAL))
        )

        assertEquals("local-new", rebindOrphanedTask(task, emptySet(), listOf(imported))?.modelId)
    }

    private fun task(modelId: String, messages: List<Message>): Task = Task(
        id = "task-1",
        title = "describe this image",
        prompt = "",
        projectId = "default",
        branch = "main",
        modelId = modelId,
        status = TaskStatus.COMPLETED,
        createdAt = 0,
        updatedAt = 0,
        messages = messages
    )

    private fun agentMessage(modelName: String, provider: ModelProvider): Message = Message(
        id = "msg-1",
        role = MessageRole.AGENT,
        text = "The image shows...",
        at = 0,
        modelName = modelName,
        provider = provider
    )
}
