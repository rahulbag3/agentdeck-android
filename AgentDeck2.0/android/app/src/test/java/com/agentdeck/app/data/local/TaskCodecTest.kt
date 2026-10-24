package com.agentdeck.app.data.local

import com.agentdeck.app.data.ActivityEvent
import com.agentdeck.app.data.ChangeType
import com.agentdeck.app.data.ChangedFile
import com.agentdeck.app.data.CheckRun
import com.agentdeck.app.data.CheckStatus
import com.agentdeck.app.data.DeliveryState
import com.agentdeck.app.data.DiffLine
import com.agentdeck.app.data.DiffLineType
import com.agentdeck.app.data.Failure
import com.agentdeck.app.data.Message
import com.agentdeck.app.data.MessageRole
import com.agentdeck.app.data.ModelProvider
import com.agentdeck.app.data.PermissionRequest
import com.agentdeck.app.data.PlanState
import com.agentdeck.app.data.PlanStep
import com.agentdeck.app.data.RepoContext
import com.agentdeck.app.data.RepoFile
import com.agentdeck.app.data.ReviewDecision
import com.agentdeck.app.data.Task
import com.agentdeck.app.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskCodecTest {

    @Test
    fun `round trip preserves the complete task record`() {
        val task = Task(
            id = "t-1",
            title = "Fix the parser",
            prompt = "Fix the parser without changing the public API.",
            projectId = "orbit-api",
            branch = "fix/parser",
            modelId = "cloud-byok",
            status = TaskStatus.IN_PROGRESS,
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_100_000,
            messages = listOf(
                Message("m-1", MessageRole.USER, "Fix it.", 1_700_000_000_100),
                Message(
                    id = "m-2",
                    role = MessageRole.AGENT,
                    text = "Here is a plan.",
                    at = 1_700_000_000_200,
                    delivery = DeliveryState.DELIVERED,
                    cloud = true,
                    modelName = "mock-gpt",
                    provider = ModelProvider.CLOUD
                )
            ),
            activity = listOf(ActivityEvent("cloud", "Request sent", 1_700_000_000_300)),
            changedFiles = listOf(
                ChangedFile(
                    id = "f-1",
                    path = "src/parser.ts",
                    oldPath = "src/old-parser.ts",
                    changeType = ChangeType.RENAMED,
                    additions = 2,
                    deletions = 1,
                    language = "typescript",
                    diffLines = listOf(
                        DiffLine(DiffLineType.CONTEXT, 1, 1, "const x = 1"),
                        DiffLine(DiffLineType.DELETE, 2, null, "const y = 2"),
                        DiffLine(DiffLineType.ADD, null, 2, "const y = 3")
                    )
                )
            ),
            checks = listOf(
                CheckRun(
                    id = "c-1",
                    type = "test",
                    name = "Unit tests",
                    status = CheckStatus.PASSED,
                    summary = "All good",
                    command = "npm test",
                    duration = "12s",
                    output = "ok"
                )
            ),
            decisions = listOf(ReviewDecision("changes_requested", "Tighten the parser.", 1_700_000_000_400)),
            permissionRequest = PermissionRequest("Install package", "Needs network", "npm install"),
            failure = Failure("Type check failed", "One error", "src/parser.ts(2,3)", 1_700_000_000_500),
            plan = listOf(
                PlanStep("Read code", PlanState.DONE),
                PlanStep("Patch code", PlanState.ACTIVE),
                PlanStep("Run checks", PlanState.TODO)
            ),
            analysisOnly = true,
            repoContextRequested = true,
            repoContext = RepoContext(
                repo = "acme/orbit-api",
                branch = "main",
                description = "Test repo",
                visibility = "private",
                treeSummary = listOf("src/", "src/parser.ts"),
                files = listOf(RepoFile("src/parser.ts", "const x = 1", "source")),
                digest = "abc123",
                truncated = true,
                fetchedAt = 1_700_000_000_600
            ),
            cloudModel = "mock-gpt",
            activeRun = true
        )

        val entity = TaskCodec.toEntity(task)
        val decoded = TaskCodec.fromEntity(entity)

        assertEquals(task, decoded)
    }

    @Test
    fun `invalid payload is ignored instead of crashing the restore`() {
        val entity = TaskEntity(
            id = "t-bad",
            title = "Bad",
            prompt = "",
            projectId = "orbit-api",
            branch = "",
            modelId = "gpt-4o",
            status = "FAILED",
            createdAt = 0,
            updatedAt = 0,
            payloadJson = "not json"
        )

        assertNull(TaskCodec.fromEntity(entity))
    }

    @Test
    fun `unknown nested entries are skipped and the rest survive`() {
        val task = Task(
            id = "t-2",
            title = "Partial",
            prompt = "Keep what can be read.",
            projectId = "orbit-api",
            branch = "main",
            modelId = "gpt-4o",
            status = TaskStatus.NEEDS_INPUT,
            createdAt = 1,
            updatedAt = 2,
            messages = listOf(Message("m-1", MessageRole.USER, "hello", 3))
        )
        val entity = TaskCodec.toEntity(task)
        val damaged = entity.copy(
            payloadJson = entity.payloadJson.replace("\"role\":\"USER\"", "\"role\":\"NOT_A_ROLE\"")
        )

        val decoded = TaskCodec.fromEntity(damaged)

        assertEquals(emptyList<Message>(), decoded?.messages)
        assertEquals(TaskStatus.NEEDS_INPUT, decoded?.status)
    }

    @Test
    fun `settings round trip preserves workspace choices`() {
        val settings = com.agentdeck.app.data.AppSettings(
            activeProjectId = "mobile-ui",
            defaultProjectId = "docs-site",
            defaultModelId = "qwen-1.5b",
            demoNoticeDismissed = true
        )

        val entity = settings.toEntity()
        val decoded = entity.toModel()

        assertEquals(settings, decoded)
        assertEquals(1, entity.id)
    }

    @Test
    fun `round trip preserves retry attempts`() {
        val task = Task(
            id = "t-retry",
            title = "Retry",
            prompt = "Retry the cloud request.",
            projectId = "orbit-api",
            branch = "main",
            modelId = "cloud-byok",
            status = TaskStatus.NEEDS_INPUT,
            createdAt = 1,
            updatedAt = 2,
            retryAttempts = 2
        )

        val decoded = TaskCodec.fromEntity(TaskCodec.toEntity(task))

        assertEquals(2, decoded?.retryAttempts)
    }
}
