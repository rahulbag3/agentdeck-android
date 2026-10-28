package com.agentdeck.app.data.local

import com.agentdeck.app.data.ActivityEvent
import com.agentdeck.app.data.Attachment
import com.agentdeck.app.data.ChangedFile
import com.agentdeck.app.data.CheckRun
import com.agentdeck.app.data.CheckStatus
import com.agentdeck.app.data.DeliveryState
import com.agentdeck.app.data.DiffLine
import com.agentdeck.app.data.DiffLineType
import com.agentdeck.app.data.Failure
import com.agentdeck.app.data.TaskKind
import com.agentdeck.app.data.Message
import com.agentdeck.app.data.MessageRole
import com.agentdeck.app.data.ModelProvider
import com.agentdeck.app.data.PermissionRequest
import com.agentdeck.app.data.PlanState
import com.agentdeck.app.data.PlanStep
import com.agentdeck.app.data.ChangeType
import com.agentdeck.app.data.RepoContext
import com.agentdeck.app.data.RepoFile
import com.agentdeck.app.data.ReviewDecision
import com.agentdeck.app.data.Task
import com.agentdeck.app.data.TaskStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON codec for the nested task record.
 *
 * The encoded payload is additive: unknown keys are ignored on read, and
 * missing lists decode as empty. A task row that cannot be parsed is skipped
 * by the store rather than crashing the workspace.
 */
internal object TaskCodec {

    fun toEntities(tasks: List<Task>): List<TaskEntity> = tasks.map(::toEntity)

    fun toEntity(task: Task): TaskEntity = TaskEntity(
        id = task.id,
        title = task.title,
        prompt = task.prompt,
        projectId = task.projectId,
        branch = task.branch,
        modelId = task.modelId,
        status = task.status.name,
        createdAt = task.createdAt,
        updatedAt = task.updatedAt,
        payloadJson = JSONObject().apply {
            put("analysisOnly", task.analysisOnly)
            put("agentMode", task.agentMode)
            put("systemPrompt", task.systemPrompt)
            put("kind", task.kind.name)
            put("repoContextRequested", task.repoContextRequested)
            put("cloudModel", task.cloudModel ?: JSONObject.NULL)
            put("activeRun", task.activeRun)
            put("messages", JSONArray().also { array -> task.messages.forEach { array.put(messageToJson(it)) } })
            put("activity", JSONArray().also { array -> task.activity.forEach { array.put(activityToJson(it)) } })
            put("changedFiles", JSONArray().also { array -> task.changedFiles.forEach { array.put(fileToJson(it)) } })
            put("checks", JSONArray().also { array -> task.checks.forEach { array.put(checkToJson(it)) } })
            put("decisions", JSONArray().also { array -> task.decisions.forEach { array.put(decisionToJson(it)) } })
            put("permission", task.permissionRequest?.let(::permissionToJson) ?: JSONObject.NULL)
            put("failure", task.failure?.let(::failureToJson) ?: JSONObject.NULL)
            put("plan", task.plan?.let { plan ->
                JSONArray().also { array -> plan.forEach { array.put(planStepToJson(it)) } }
            } ?: JSONObject.NULL)
            put("repoContext", task.repoContext?.let(::repoContextToJson) ?: JSONObject.NULL)
            put("retryAttempts", task.retryAttempts)
        }.toString()
    )

    fun fromEntity(entity: TaskEntity): Task? {
        val payload = runCatching { JSONObject(entity.payloadJson) }.getOrNull() ?: return null
        val status = enumOrNull<TaskStatus>(entity.status) ?: return null
        return Task(
            id = entity.id,
            title = entity.title,
            prompt = entity.prompt,
            projectId = entity.projectId,
            branch = entity.branch,
            modelId = entity.modelId,
            status = status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            messages = payload.objects("messages").mapNotNull(::messageFromJson),
            activity = payload.objects("activity").mapNotNull(::activityFromJson),
            changedFiles = payload.objects("changedFiles").mapNotNull(::fileFromJson),
            checks = payload.objects("checks").mapNotNull(::checkFromJson),
            decisions = payload.objects("decisions").mapNotNull(::decisionFromJson),
            permissionRequest = payload.objectOrNull("permission")?.let(::permissionFromJson),
            failure = payload.objectOrNull("failure")?.let(::failureFromJson),
            plan = payload.objectsOrNull("plan")?.mapNotNull(::planStepFromJson),
            analysisOnly = payload.optBoolean("analysisOnly", false),
            agentMode = payload.optBoolean("agentMode", false),
            systemPrompt = payload.optString("systemPrompt", ""),
            kind = enumOrNull<TaskKind>(payload.optString("kind", "CODING")) ?: TaskKind.CODING,
            repoContextRequested = payload.optBoolean("repoContextRequested", false),
            repoContext = payload.objectOrNull("repoContext")?.let(::repoContextFromJson),
            cloudModel = payload.optStringOrNull("cloudModel"),
            activeRun = payload.optBoolean("activeRun", false),
            retryAttempts = payload.optInt("retryAttempts", 0)
        )
    }

    private fun messageToJson(message: Message) = JSONObject().apply {
        put("id", message.id)
        put("role", message.role.name)
        put("text", message.text)
        put("at", message.at)
        put("delivery", message.delivery.name)
        put("localPreview", message.localPreview)
        put("cloud", message.cloud)
        put("modelName", message.modelName ?: JSONObject.NULL)
        put("provider", message.provider?.name ?: JSONObject.NULL)
        put("attachments", JSONArray().also { array ->
            message.attachments.forEach { array.put(attachmentToJson(it)) }
        })
    }

    private fun attachmentToJson(attachment: Attachment) = JSONObject().apply {
        put("name", attachment.name)
        put("path", attachment.path)
        put("size", attachment.size)
    }

    private fun attachmentFromJson(json: JSONObject): Attachment? {
        val name = json.optString("name").takeIf { it.isNotEmpty() } ?: return null
        val path = json.optString("path").takeIf { it.isNotEmpty() } ?: return null
        return Attachment(name = name, path = path, size = json.optLong("size", 0L))
    }

    private fun messageFromJson(json: JSONObject): Message? {
        val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val role = enumOrNull<MessageRole>(json.optString("role")) ?: return null
        return Message(
            id = id,
            role = role,
            text = json.optString("text"),
            at = json.optLong("at"),
            delivery = enumOrNull<DeliveryState>(json.optString("delivery")) ?: DeliveryState.DELIVERED,
            localPreview = json.optBoolean("localPreview", false),
            cloud = json.optBoolean("cloud", false),
            modelName = json.optStringOrNull("modelName"),
            provider = enumOrNull<ModelProvider>(json.optString("provider")),
            attachments = json.objects("attachments").mapNotNull(::attachmentFromJson)
        )
    }

    private fun activityToJson(event: ActivityEvent) = JSONObject().apply {
        put("type", event.type)
        put("text", event.text)
        put("at", event.at)
    }

    private fun activityFromJson(json: JSONObject): ActivityEvent? {
        val text = json.optString("text").takeIf { it.isNotEmpty() } ?: return null
        return ActivityEvent(json.optString("type", "status"), text, json.optLong("at"))
    }

    private fun fileToJson(file: ChangedFile) = JSONObject().apply {
        put("id", file.id)
        put("path", file.path)
        put("oldPath", file.oldPath ?: JSONObject.NULL)
        put("changeType", file.changeType.name)
        put("additions", file.additions)
        put("deletions", file.deletions)
        put("language", file.language)
        put("unavailableReason", file.unavailableReason ?: JSONObject.NULL)
        put("diffLines", JSONArray().also { array ->
            file.diffLines.forEach { line ->
                array.put(JSONObject().apply {
                    put("type", line.type.name)
                    put("oldNumber", line.oldNumber ?: JSONObject.NULL)
                    put("newNumber", line.newNumber ?: JSONObject.NULL)
                    put("text", line.text)
                })
            }
        })
    }

    private fun fileFromJson(json: JSONObject): ChangedFile? {
        val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val path = json.optString("path").takeIf { it.isNotEmpty() } ?: return null
        val changeType = enumOrNull<ChangeType>(json.optString("changeType")) ?: return null
        return ChangedFile(
            id = id,
            path = path,
            oldPath = json.optStringOrNull("oldPath"),
            changeType = changeType,
            additions = json.optInt("additions", 0),
            deletions = json.optInt("deletions", 0),
            language = json.optString("language", ""),
            diffLines = json.objects("diffLines").mapNotNull { line ->
                val type = enumOrNull<DiffLineType>(line.optString("type")) ?: return@mapNotNull null
                DiffLine(
                    type = type,
                    oldNumber = line.optIntOrNull("oldNumber"),
                    newNumber = line.optIntOrNull("newNumber"),
                    text = line.optString("text")
                )
            },
            unavailableReason = json.optStringOrNull("unavailableReason")
        )
    }

    private fun checkToJson(check: CheckRun) = JSONObject().apply {
        put("id", check.id)
        put("type", check.type)
        put("name", check.name)
        put("status", check.status.name)
        put("summary", check.summary)
        put("command", check.command)
        put("duration", check.duration)
        put("output", check.output)
    }

    private fun checkFromJson(json: JSONObject): CheckRun? {
        val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
        return CheckRun(
            id = id,
            type = json.optString("type", "check"),
            name = json.optString("name", "Check"),
            status = enumOrNull<CheckStatus>(json.optString("status")) ?: CheckStatus.NOT_RUN,
            summary = json.optString("summary"),
            command = json.optString("command"),
            duration = json.optString("duration"),
            output = json.optString("output")
        )
    }

    private fun decisionToJson(decision: ReviewDecision) = JSONObject().apply {
        put("decision", decision.decision)
        put("feedback", decision.feedback)
        put("at", decision.at)
    }

    private fun decisionFromJson(json: JSONObject): ReviewDecision? {
        val decision = json.optString("decision").takeIf { it.isNotEmpty() } ?: return null
        return ReviewDecision(decision, json.optString("feedback"), json.optLong("at"))
    }

    private fun permissionToJson(permission: PermissionRequest) = JSONObject().apply {
        put("title", permission.title)
        put("body", permission.body)
        put("command", permission.command)
        put("pending", permission.pending)
        put("requestId", permission.requestId ?: JSONObject.NULL)
    }

    private fun permissionFromJson(json: JSONObject) = PermissionRequest(
        title = json.optString("title"),
        body = json.optString("body"),
        command = json.optString("command"),
        pending = json.optBoolean("pending", true),
        requestId = json.optStringOrNull("requestId")
    )

    private fun failureToJson(failure: Failure) = JSONObject().apply {
        put("title", failure.title)
        put("summary", failure.summary)
        put("detail", failure.detail)
        put("at", failure.at)
    }

    private fun failureFromJson(json: JSONObject) = Failure(
        title = json.optString("title"),
        summary = json.optString("summary"),
        detail = json.optString("detail"),
        at = json.optLong("at")
    )

    private fun planStepToJson(step: PlanStep) = JSONObject().apply {
        put("text", step.text)
        put("state", step.state.name)
    }

    private fun planStepFromJson(json: JSONObject): PlanStep? {
        val text = json.optString("text").takeIf { it.isNotEmpty() } ?: return null
        return PlanStep(text, enumOrNull<PlanState>(json.optString("state")) ?: PlanState.TODO)
    }

    private fun repoContextToJson(context: RepoContext) = JSONObject().apply {
        put("repo", context.repo)
        put("branch", context.branch)
        put("description", context.description)
        put("visibility", context.visibility)
        put("treeSummary", JSONArray().also { array -> context.treeSummary.forEach { array.put(it) } })
        put("files", JSONArray().also { array ->
            context.files.forEach { file ->
                array.put(JSONObject().apply {
                    put("path", file.path)
                    put("content", file.content)
                    put("role", file.role)
                })
            }
        })
        put("digest", context.digest)
        put("truncated", context.truncated)
        put("fetchedAt", context.fetchedAt)
    }

    private fun repoContextFromJson(json: JSONObject): RepoContext = RepoContext(
        repo = json.optString("repo"),
        branch = json.optString("branch"),
        description = json.optString("description"),
        visibility = json.optString("visibility", "public"),
        treeSummary = json.strings("treeSummary"),
        files = json.objects("files").mapNotNull { file ->
            val path = file.optString("path").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            RepoFile(path, file.optString("content"), file.optString("role", "source"))
        },
        digest = json.optString("digest"),
        truncated = json.optBoolean("truncated", false),
        fetchedAt = json.optLong("fetchedAt")
    )

    private inline fun <reified T : Enum<T>> enumOrNull(raw: String): T? =
        enumValues<T>().firstOrNull { it.name == raw }

    private fun JSONObject.objects(key: String): List<JSONObject> =
        objectsOrNull(key) ?: emptyList()

    private fun JSONObject.objectsOrNull(key: String): List<JSONObject>? {
        if (!has(key) || isNull(key)) return null
        val array = optJSONArray(key) ?: return null
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun JSONObject.objectOrNull(key: String): JSONObject? =
        if (!has(key) || isNull(key)) null else optJSONObject(key)

    private fun JSONObject.strings(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotEmpty() }
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)
}

/** Non-secret settings are stored in Room alongside the tasks. */
internal fun com.agentdeck.app.data.AppSettings.toEntity(): AppSettingsEntity = AppSettingsEntity(
    activeProjectId = activeProjectId,
    defaultProjectId = defaultProjectId,
    defaultModelId = defaultModelId,
    demoNoticeDismissed = demoNoticeDismissed
)

internal fun AppSettingsEntity.toModel(): com.agentdeck.app.data.AppSettings =
    com.agentdeck.app.data.AppSettings(
        activeProjectId = activeProjectId,
        defaultProjectId = defaultProjectId,
        defaultModelId = defaultModelId,
        demoNoticeDismissed = demoNoticeDismissed
    )
