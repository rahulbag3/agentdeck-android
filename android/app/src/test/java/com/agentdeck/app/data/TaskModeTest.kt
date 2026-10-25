package com.agentdeck.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskModeTest {

    private fun task(
        messages: List<Message> = emptyList(),
        activity: List<ActivityEvent> = emptyList(),
        analysisOnly: Boolean = false
    ) = Task(
        id = "t1",
        title = "Task",
        prompt = "prompt",
        projectId = "p1",
        branch = "main",
        modelId = "m1",
        status = TaskStatus.NEEDS_INPUT,
        createdAt = 1L,
        updatedAt = 1L,
        messages = messages,
        activity = activity,
        analysisOnly = analysisOnly
    )

    private val localModel = ModelOption(
        id = "m1", name = "Local", provider = ModelProvider.LOCAL,
        providerLabel = "device", localState = LocalModelState.UNLOADED
    )

    private val cloudModel = ModelOption(
        id = "m1", name = "Cloud", provider = ModelProvider.CLOUD,
        providerLabel = "api", cloudState = CloudModelState.READY
    )

    @Test
    fun `model row wins when present`() {
        assertTrue(taskLooksLocal(task(), localModel))
        assertFalse(taskLooksCloud(task(), localModel))
        assertFalse(taskLooksLocal(task(), cloudModel))
        assertTrue(taskLooksCloud(task(), cloudModel))
    }

    @Test
    fun `unloaded local model is still local`() {
        // After a restart the row exists with UNLOADED state; classification must hold.
        assertTrue(taskLooksLocal(task(), localModel.copy(localState = LocalModelState.UNLOADED)))
    }

    @Test
    fun `missing local model row falls back to transcript evidence`() {
        val messages = listOf(
            Message(id = "1", role = MessageRole.AGENT, text = "hi", at = 1, provider = ModelProvider.LOCAL)
        )
        assertTrue(taskLooksLocal(task(messages = messages), option = null))
        assertFalse(taskLooksCloud(task(messages = messages), option = null))
    }

    @Test
    fun `missing cloud model row falls back to evidence`() {
        val cloudMessage = Message(
            id = "1", role = MessageRole.AGENT, text = "hi", at = 1,
            provider = ModelProvider.CLOUD, cloud = true
        )
        assertTrue(taskLooksCloud(task(messages = listOf(cloudMessage)), option = null))
        assertTrue(taskLooksCloud(task(analysisOnly = true), option = null))
    }

    @Test
    fun `activity evidence classifies orphaned tasks`() {
        val localActivity = task(activity = listOf(ActivityEvent("local-engine", "Generating", 1)))
        assertTrue(taskLooksLocal(localActivity, option = null))

        val cloudActivity = task(activity = listOf(ActivityEvent("cloud", "Analysis ready", 1)))
        assertTrue(taskLooksCloud(cloudActivity, option = null))
    }

    @Test
    fun `no row and no evidence is neither local nor cloud`() {
        assertFalse(taskLooksLocal(task(), option = null))
        assertFalse(taskLooksCloud(task(), option = null))
    }
}
