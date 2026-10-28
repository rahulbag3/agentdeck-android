package com.agentdeck.app.data

const val CLOUD_MODEL_ID = "cloud-byok"
const val HOST_MODEL_ID = "host-agent"

enum class TaskStatus(val label: String) {
    PENDING("Pending"),
    IN_PROGRESS("In progress"),
    NEEDS_INPUT("Needs input"),
    REVIEW("Review"),
    FAILED("Failed"),
    COMPLETED("Completed")
}

enum class ModelProvider { HOST, LOCAL, CLOUD }

/** What the task does: general conversation vs. working a code workspace. */
enum class TaskKind { CHAT, CODING }

enum class LocalModelState(val label: String) {
    READY("Ready"),
    LOADING("Loading…"),
    UNLOADED("Not loaded"),
    IMPORTING("Importing…"),
    NOT_IMPORTED("Not imported")
}

enum class CloudModelState(val label: String) {
    READY("Ready"),
    NOT_CONFIGURED("Not configured")
}

data class ModelOption(
    val id: String,
    val name: String,
    val provider: ModelProvider,
    val providerLabel: String,
    val quantization: String? = null,
    val sizeGB: Double? = null,
    val contextK: Int? = null,
    val minRamGB: Int? = null,
    val localState: LocalModelState? = null,
    val cloudState: CloudModelState? = null
) {
    val isLocal: Boolean get() = provider == ModelProvider.LOCAL
    val isCloud: Boolean get() = provider == ModelProvider.CLOUD
    val isHost: Boolean get() = provider == ModelProvider.HOST
    val isReady: Boolean
        get() = when (provider) {
            ModelProvider.LOCAL -> localState == LocalModelState.READY
            ModelProvider.CLOUD -> cloudState == CloudModelState.READY
            ModelProvider.HOST -> true
        }
    val meta: String
        get() = when (provider) {
            ModelProvider.LOCAL -> listOfNotNull(
                quantization,
                sizeGB?.let { "$it GB" },
                contextK?.let { "${it}K context" }
            ).joinToString(" · ")
            ModelProvider.CLOUD -> "$providerLabel · direct from this phone"
            ModelProvider.HOST -> "$providerLabel · host"
        }
}

data class CloudConfig(
    val baseUrl: String = "",
    val model: String = "",
    val keySaved: Boolean = false
) {
    /** Endpoint + key present; a model can still be chosen per message. */
    val endpointReady: Boolean
        get() = keySaved && CloudClient.baseUrlError(baseUrl) == null
    val configured: Boolean
        get() = endpointReady && model.isNotBlank()
}

/** A saved OpenAI-compatible endpoint. The API key lives in SecureStore. */
data class CloudProfile(
    val id: String,
    val label: String,
    val baseUrl: String,
    val models: List<String> = emptyList(),
    val lastModel: String = "",
    val keySaved: Boolean = false
)

data class GitHubConfig(
    val repo: String = "",
    val branch: String = "",
    val tokenSaved: Boolean = false
) {
    val configured: Boolean get() = GitHubClient.parseRepo(repo) != null
}

data class HostConfig(
    val baseUrl: String = "",
    val tokenSaved: Boolean = false,
    val allowInsecureLan: Boolean = false,
    val name: String = "",
    val platform: String = "",
    val projectIds: List<String> = emptyList()
) {
    val configured: Boolean get() = tokenSaved && baseUrl.isNotBlank()
    val encrypted: Boolean get() = baseUrl.startsWith("https://")
}

sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Testing : ConnectionTest
    data class Success(val message: String) : ConnectionTest
    data class Failure(val message: String) : ConnectionTest
}

data class GitHubRepoInfo(
    val fullName: String,
    val defaultBranch: String,
    val description: String,
    val private: Boolean,
    val sizeKb: Long
)

data class RepoFile(
    val path: String,
    val content: String,
    val role: String
)

data class RepoContext(
    val repo: String,
    val branch: String,
    val description: String,
    val visibility: String,
    val treeSummary: List<String>,
    val files: List<RepoFile>,
    val digest: String,
    val truncated: Boolean,
    val fetchedAt: Long
)

data class Project(
    val id: String,
    val name: String,
    val repo: String,
    val defaultBranch: String
)

data class Host(
    val name: String,
    val platform: String,
    val transport: String,
    val connected: Boolean,
    val lastSeen: Long
)

data class AppSettings(
    val activeProjectId: String,
    val defaultProjectId: String,
    val defaultModelId: String,
    /** Legacy column kept for Room schema v1 and older backups; no longer read. */
    val demoNoticeDismissed: Boolean = false
)

enum class MessageRole { USER, AGENT, SYSTEM }

enum class DeliveryState { DELIVERED, SENDING, QUEUED, FAILED }

/**
 * A file the user attached to a message. [path] is relative to the project
 * workspace, so the agent's `read`/`grep` tools can open it directly.
 */
data class Attachment(
    val name: String,
    val path: String,
    val size: Long = 0L
)

data class Message(
    val id: String,
    val role: MessageRole,
    val text: String,
    val at: Long,
    val delivery: DeliveryState = DeliveryState.DELIVERED,
    val localPreview: Boolean = false,
    val cloud: Boolean = false,
    val modelName: String? = null,
    val provider: ModelProvider? = null,
    val attachments: List<Attachment> = emptyList()
)

data class ActivityEvent(
    val type: String,
    val text: String,
    val at: Long
)

data class PermissionRequest(
    val title: String,
    val body: String,
    val command: String,
    val pending: Boolean = true,
    /** Host-assigned id for a live run; null outside a live run. */
    val requestId: String? = null
)

data class Failure(
    val title: String,
    val summary: String,
    val detail: String,
    val at: Long
)

data class PlanStep(
    val text: String,
    val state: PlanState
)

enum class PlanState { TODO, ACTIVE, DONE, FAILED }

enum class ChangeType(val label: String) {
    MODIFIED("Modified"),
    ADDED("Added"),
    DELETED("Deleted"),
    RENAMED("Renamed")
}

enum class DiffLineType { CONTEXT, ADD, DELETE }

data class DiffLine(
    val type: DiffLineType,
    val oldNumber: Int?,
    val newNumber: Int?,
    val text: String
)

data class ChangedFile(
    val id: String,
    val path: String,
    val oldPath: String? = null,
    val changeType: ChangeType,
    val additions: Int,
    val deletions: Int,
    val language: String,
    val diffLines: List<DiffLine> = emptyList(),
    val unavailableReason: String? = null
) {
    val diffAvailable: Boolean get() = diffLines.isNotEmpty() && unavailableReason == null
}

enum class CheckStatus(val label: String) {
    PASSED("Passed"),
    FAILED("Failed"),
    SKIPPED("Skipped"),
    NOT_RUN("Not run")
}

data class CheckRun(
    val id: String,
    val type: String,
    val name: String,
    val status: CheckStatus,
    val summary: String,
    val command: String = "",
    val duration: String = "",
    val output: String = ""
)

data class ReviewDecision(
    val decision: String,
    val feedback: String,
    val at: Long
)

data class Task(
    val id: String,
    val title: String,
    val prompt: String,
    val projectId: String,
    val branch: String,
    val modelId: String,
    val status: TaskStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<Message> = emptyList(),
    val activity: List<ActivityEvent> = emptyList(),
    val changedFiles: List<ChangedFile> = emptyList(),
    val checks: List<CheckRun> = emptyList(),
    val decisions: List<ReviewDecision> = emptyList(),
    val permissionRequest: PermissionRequest? = null,
    val failure: Failure? = null,
    val plan: List<PlanStep>? = null,
    val analysisOnly: Boolean = false,
    /** User-provided system prompt for this session; blank = default behaviour. */
    val systemPrompt: String = "",
    /** Cloud task that acts on the project workspace with tools (files/shell/git). */
    val agentMode: Boolean = false,
    /** CHAT: general assistant with web/memory tools. CODING: workspace tools + git. */
    val kind: TaskKind = TaskKind.CODING,
    val repoContextRequested: Boolean = false,
    val repoContext: RepoContext? = null,
    val cloudModel: String? = null,
    /**
     * True only while a request started by this app is genuinely running.
     * Seeded snapshots can be IN_PROGRESS without ever having a live job.
     */
    val activeRun: Boolean = false,
    /** Track retry attempts for robust error handling */
    val retryAttempts: Int = 0
)

data class AgentDeckData(
    val host: Host,
    val projects: List<Project>,
    val models: List<ModelOption>,
    val settings: AppSettings,
    val tasks: List<Task>
)

data class NewTaskDraft(
    val title: String = "",
    val prompt: String = "",
    val projectId: String = "",
    val branch: String = "",
    val branchTouched: Boolean = false,
    val modelId: String = "",
    val error: String? = null,
    val errorKind: String? = null,
    val useRepoContext: Boolean = true,
    val agentMode: Boolean = false,
    val taskKind: TaskKind = TaskKind.CODING,
    /** Reusable instruction set applied to this task's system prompt. */
    val instructionId: String = ""
)

enum class RootTab { HOME, TASKS, PROJECTS, SETTINGS }

/**
 * Provider classification that survives a missing model row — for example a
 * local model whose file was deleted, or after the ephemeral local state
 * reset. Falls back to transcript evidence so an orphaned task still behaves
 * like its original provider (switchable, correct send path) instead of
 * silently turning into a host task.
 */
fun taskLooksLocal(task: Task, option: ModelOption?): Boolean =
    if (option != null) option.isLocal
    else task.messages.any { it.provider == ModelProvider.LOCAL } ||
        task.activity.any { it.type == "local" || it.type == "local-engine" }

fun taskLooksCloud(task: Task, option: ModelOption?): Boolean =
    if (option != null) option.isCloud
    else task.analysisOnly ||
        task.messages.any { it.provider == ModelProvider.CLOUD || it.cloud } ||
        task.activity.any { it.type == "cloud" || it.type == "cloud-error" }

enum class TaskTab(val label: String) { CHAT("Chat"), CHANGES("Changes"), CHECKS("Checks") }

sealed interface Screen {
    data class Root(val tab: RootTab) : Screen
    data class TaskScreen(val taskId: String, val tab: TaskTab = TaskTab.CHAT) : Screen
    data object NewTask : Screen
}

sealed interface Sheet {
    data object Projects : Sheet
    data object Models : Sheet
    data object About : Sheet
    data object CloudSetup : Sheet
    data object Instructions : Sheet
    data object GitHubSetup : Sheet
    data object HostSetup : Sheet
    data class RepoContextSheet(val taskId: String) : Sheet
    data class TaskModels(val taskId: String) : Sheet
    data class Approve(val taskId: String) : Sheet
    data class RequestChanges(val taskId: String) : Sheet
    data class Confirm(val kind: ConfirmKind) : Sheet
}

enum class ConfirmKind { DISCONNECT, RESET, REMOVE_CLOUD, REMOVE_GITHUB, REMOVE_HOST }

enum class StatusFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    NEEDS_INPUT("Needs input"),
    REVIEW("Review"),
    PENDING("Pending"),
    FAILED("Failed"),
    COMPLETED("Completed")
}

fun TaskStatus.matchesFilter(filter: StatusFilter): Boolean = when (filter) {
    StatusFilter.ALL -> true
    StatusFilter.ACTIVE -> this == TaskStatus.IN_PROGRESS
    StatusFilter.NEEDS_INPUT -> this == TaskStatus.NEEDS_INPUT
    StatusFilter.REVIEW -> this == TaskStatus.REVIEW
    StatusFilter.PENDING -> this == TaskStatus.PENDING
    StatusFilter.FAILED -> this == TaskStatus.FAILED
    StatusFilter.COMPLETED -> this == TaskStatus.COMPLETED
}
