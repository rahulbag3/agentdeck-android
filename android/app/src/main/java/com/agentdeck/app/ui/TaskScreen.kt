package com.agentdeck.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentdeck.app.data.ActivityEvent
import com.agentdeck.app.data.AgentDeckState
import com.agentdeck.app.data.ChangedFile
import com.agentdeck.app.data.CheckRun
import com.agentdeck.app.data.CheckStatus
import com.agentdeck.app.data.DeliveryState
import com.agentdeck.app.data.DiffLineType
import com.agentdeck.app.data.Failure
import com.agentdeck.app.data.LocalModelState
import com.agentdeck.app.data.Message
import com.agentdeck.app.data.MessageRole
import com.agentdeck.app.data.ModelOption
import com.agentdeck.app.data.ModelProvider
import com.agentdeck.app.data.PermissionRequest
import com.agentdeck.app.data.PlanState
import com.agentdeck.app.data.Project
import com.agentdeck.app.data.Task
import com.agentdeck.app.data.TaskKind
import com.agentdeck.app.data.TaskStatus
import com.agentdeck.app.data.TaskTab
import com.agentdeck.app.ui.theme.Surface as AdSurface
import com.agentdeck.app.ui.theme.AmberInk
import com.agentdeck.app.ui.theme.AmberSoft
import com.agentdeck.app.ui.theme.BlueInk
import com.agentdeck.app.ui.theme.BlueSoft
import com.agentdeck.app.ui.theme.GrayInk
import com.agentdeck.app.ui.theme.GraySoft
import com.agentdeck.app.ui.theme.GreenInk
import com.agentdeck.app.ui.theme.GreenSoft
import com.agentdeck.app.ui.theme.Ink
import com.agentdeck.app.ui.theme.InkSoft
import com.agentdeck.app.ui.theme.Line
import com.agentdeck.app.ui.theme.Mono
import com.agentdeck.app.ui.theme.Muted
import com.agentdeck.app.ui.theme.Muted2
import com.agentdeck.app.ui.theme.RedInk
import com.agentdeck.app.ui.theme.RedSoft
import com.agentdeck.app.ui.theme.Surface2
import com.agentdeck.app.ui.theme.Surface3
import com.agentdeck.app.ui.theme.VioletInk
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TaskScreen(state: AgentDeckState, taskId: String) {
    val task = state.task(taskId)
    if (task == null) {
        Column(Modifier.fillMaxSize().background(Surface2)) {
            AdBackTopBar(title = "Task not found", onBack = state::back)
            Box(Modifier.weight(1f).padding(16.dp)) {
                AdEmptyState(
                    icon = Icons.Outlined.Description,
                    title = "This task is unavailable",
                    text = "It may have been removed from this workspace.",
                    modifier = Modifier.align(Alignment.TopCenter),
                    action = { AdButton("Back to tasks", onClick = { state.selectRoot(com.agentdeck.app.data.RootTab.TASKS) }, small = true) }
                )
            }
        }
        return
    }
    val project = state.project(task.projectId) ?: state.activeProject
    val local = state.isLocalTask(task)
    val cloud = state.isCloudTask(task)
    val model = state.model(task.modelId)
    val tab = (state.screen as? com.agentdeck.app.data.Screen.TaskScreen)?.tab ?: TaskTab.CHAT

    Column(Modifier.fillMaxSize().background(if (tab == TaskTab.CHAT) AdSurface else Surface2)) {
        TaskTopBar(task = task, project = project, local = local, cloud = cloud, onBack = state::back)
        TaskTabs(task = task, active = tab, onSelect = state::selectTaskTab)
        when (tab) {
            TaskTab.CHAT -> {
                ChatContent(
                    state = state,
                    task = task,
                    project = project,
                    local = local,
                    cloud = cloud,
                    modifier = Modifier.weight(1f)
                )
                ComposerBar(state = state, task = task, model = model, local = local, cloud = cloud)
            }
            TaskTab.CHANGES -> {
                ChangesContent(
                    state = state,
                    task = task,
                    project = project,
                    local = local,
                    cloud = cloud,
                    modifier = Modifier.weight(1f)
                )
                if (task.status == TaskStatus.REVIEW) {
                    ReviewBar(state = state, task = task)
                }
            }
            TaskTab.CHECKS -> {
                ChecksContent(
                    state = state,
                    task = task,
                    local = local,
                    cloud = cloud,
                    model = model,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TaskTopBar(task: Task, project: Project, local: Boolean, cloud: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AdSurface)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AdIconButton(Icons.Outlined.ArrowBack, "Back", onBack, size = 36.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val scopeLabel = when {
                    cloud -> task.repoContext?.repo ?: project.name
                    else -> project.name
                }
                val branchLabel = task.branch.ifBlank { if (cloud) "analysis" else project.defaultBranch }
                Text(
                    "$scopeLabel · $branchLabel",
                    fontSize = 9.5.sp,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (local) {
                    Spacer(Modifier.width(4.dp))
                    LocalChip()
                }
                if (cloud) {
                    Spacer(Modifier.width(4.dp))
                    CloudChip()
                }
                Spacer(Modifier.width(4.dp))
                if (cloud) AnalysisTag() else if (!local) HostTag()
            }
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(task.status)
    }
}

@Composable
private fun TaskTabs(task: Task, active: TaskTab, onSelect: (TaskTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(AdSurface)) {
        Row(Modifier.fillMaxWidth()) {
            val tabs = if (task.kind == TaskKind.CHAT) listOf(TaskTab.CHAT) else TaskTab.values().toList()
            tabs.forEach { tab ->
                val selected = tab == active
                val count = when (tab) {
                    TaskTab.CHAT -> task.messages.size
                    TaskTab.CHANGES -> task.changedFiles.size
                    TaskTab.CHECKS -> task.checks.size
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.height(42.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tab.label,
                            fontSize = 11.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Ink else Muted
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            count.toString(),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) BlueInk else Muted,
                            modifier = Modifier
                                .clip(PillShape)
                                .background(if (selected) BlueSoft else Surface3)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth(if (selected) 0.8f else 0f)
                            .height(2.dp)
                            .background(if (selected) Ink else Color.Transparent)
                    )
                }
            }
        }
        AdDivider()
    }
}

@Composable
private fun ChatContent(
    state: AgentDeckState,
    task: Task,
    project: Project,
    local: Boolean,
    cloud: Boolean,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val last = task.messages.lastOrNull()
    val typing = task.id in state.cloudStreaming &&
        (last == null || last.role != MessageRole.AGENT || last.text.isBlank())
    LaunchedEffect(task.id, task.messages.size, last?.text?.length, typing) {
        delay(80)
        scroll.animateScrollTo(scroll.maxValue)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AdSurface)
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        TaskHostBanner(state, task, local, cloud)
        if (local && task.repoContext != null) {
            InfoBanner(
                title = "Snapshot not used here",
                text = "This task has a GitHub snapshot from an earlier cloud session. Local models do not read it. Switch back to cloud to use the snapshot.",
                kind = BannerKind.LOCAL,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        if (task.agentMode) {
            InfoBanner(
                title = "Agent task",
                text = "This task works in the project workspace on this phone: reading files, editing, running shell commands and git. Reads run automatically; every write, command, and commit waits for your approval.",
                kind = BannerKind.LOCAL,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        } else if (task.analysisOnly) {
            RepoContextSummary(state, task)
            Spacer(Modifier.height(10.dp))
            InfoBanner(
                title = "Read-only cloud analysis",
                text = if (task.repoContext != null) {
                    "Replies can review the attached snapshot and propose patches. Nothing is written until you apply a proposal from the Changes tab — that opens a pull request on GitHub."
                } else {
                    "This chat can plan and propose patches, but no repository snapshot is attached. Nothing is written until you apply a proposal from the Changes tab."
                },
                kind = BannerKind.CLOUD,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (task.plan != null && task.status in listOf(TaskStatus.PENDING, TaskStatus.IN_PROGRESS, TaskStatus.NEEDS_INPUT, TaskStatus.FAILED)) {
                PlanCard(task)
                Spacer(Modifier.height(12.dp))
            }
            ActivityCard(state = state, task = task)
            if (task.activity.isNotEmpty()) Spacer(Modifier.height(12.dp))
        }
        Text(
            chatDayLabel(task.createdAt),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 12.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = Muted2,
            textAlign = TextAlign.Center
        )
        task.messages.forEach { message ->
            MessageBlock(state = state, task = task, local = local, cloud = cloud, message = message)
            Spacer(Modifier.height(15.dp))
        }
        if (typing) {
            TypingBlock(modelName = state.taskModelName(task), cloud = cloud)
            Spacer(Modifier.height(12.dp))
        }
        if (!cloud) {
            if (task.plan != null && task.status in listOf(TaskStatus.PENDING, TaskStatus.IN_PROGRESS, TaskStatus.NEEDS_INPUT, TaskStatus.FAILED)) {
                PlanCard(task)
                Spacer(Modifier.height(12.dp))
            }
            ActivityCard(state = state, task = task)
            if (task.activity.isNotEmpty()) Spacer(Modifier.height(12.dp))
        }
        task.permissionRequest?.let {
            PermissionCard(state = state, task = task, request = it)
            Spacer(Modifier.height(12.dp))
        }
        task.failure?.let {
            FailureCard(state = state, task = task, failure = it)
            Spacer(Modifier.height(12.dp))
        }
        if (task.status == TaskStatus.REVIEW) {
            ReadyCard(state = state, task = task)
            Spacer(Modifier.height(12.dp))
        }
        if (task.status == TaskStatus.COMPLETED) {
            DecisionCard(task)
            if (task.decisions.isNotEmpty()) Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RepoContextSummary(state: AgentDeckState, task: Task) {
    val context = task.repoContext
    val loading = state.isRepoContextLoading(task.id)
    if (context == null) {
        if (!task.repoContextRequested) return
        val preparing = loading || task.status == TaskStatus.IN_PROGRESS
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Surface2,
            border = BorderStroke(1.dp, Line)
        ) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(14.dp), tint = Muted)
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (preparing) "Fetching GitHub context…" else "Repository context not loaded",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                    Text(
                        if (preparing) "README, manifests, and relevant source files" else "The analysis continues without file contents.",
                        fontSize = 9.sp,
                        color = Muted2
                    )
                }
                if (!preparing) {
                    Text(
                        "Retry",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.githubConfig.configured) BlueInk else Muted2,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = state.githubConfig.configured) { state.refreshRepoContext(task.id) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
        return
    }
    Surface(
        onClick = { state.openRepoContext(task.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AdSurface,
        border = BorderStroke(1.dp, Line)
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(GreenSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(14.dp), tint = GreenInk)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GitHub context", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.width(5.dp))
                    AdTag("Read-only", GreenSoft, GreenInk)
                    if (context.truncated) {
                        Spacer(Modifier.width(4.dp))
                        AdTag("Trimmed", AmberSoft, AmberInk)
                    }
                }
                Text(
                    "${context.repo}@${context.branch} · ${context.files.size} file${if (context.files.size == 1) "" else "s"}",
                    fontSize = 9.sp,
                    color = Muted2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            if (loading) {
                Text("Refreshing…", fontSize = 9.sp, color = BlueInk)
            } else {
                Text("View", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = BlueInk)
            }
        }
    }
}

@Composable
private fun MessageBlock(
    state: AgentDeckState,
    task: Task,
    local: Boolean,
    cloud: Boolean,
    message: Message
) {
    if (message.role == MessageRole.SYSTEM) {
        val lines = message.text.lines()
        val title = lines.firstOrNull().orEmpty()
        val body = lines.drop(1).joinToString("\n").trim()
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = AmberSoft,
            border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFEEDaAB))
        ) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = AmberInk)
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = AmberInk)
                    if (body.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(body, fontSize = 10.5.sp, lineHeight = 15.sp, color = AmberInk.copy(alpha = 0.88f))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(clockTime(message.at), fontSize = 8.5.sp, color = AmberInk.copy(alpha = 0.65f))
                }
            }
        }
        return
    }
    if (message.role == MessageRole.USER) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Column(
                modifier = Modifier.widthIn(max = 330.dp),
                horizontalAlignment = Alignment.End
            ) {
                Surface(
                    color = Surface3,
                    shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp, bottomStart = 15.dp, bottomEnd = 4.dp)
                ) {
                    Text(
                        message.text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = Ink
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeliveryMeta(state = state, task = task, message = message)
                }
            }
        }
        return
    }
    val provider = message.provider ?: when {
        message.cloud -> ModelProvider.CLOUD
        message.localPreview -> ModelProvider.LOCAL
        cloud -> ModelProvider.CLOUD
        local -> ModelProvider.LOCAL
        else -> ModelProvider.HOST
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                message.modelName ?: "Agent",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            when (provider) {
                ModelProvider.CLOUD -> CloudChip()
                ModelProvider.LOCAL -> LocalChip("On this phone")
                ModelProvider.HOST -> Unit
            }
        }
        Spacer(Modifier.height(3.dp))
        if (provider == ModelProvider.CLOUD) {
            MarkdownText(message.text, Modifier.fillMaxWidth())
        } else {
            Text(message.text, fontSize = 12.5.sp, lineHeight = 19.sp, color = Ink)
        }
        Spacer(Modifier.height(4.dp))
        Text(clockTime(message.at), fontSize = 9.sp, color = Muted2)
    }
}

@Composable
private fun DeliveryMeta(state: AgentDeckState, task: Task, message: Message) {
    when (message.delivery) {
        DeliveryState.SENDING -> {
            CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = Muted2)
            Text("Sending…", fontSize = 9.sp, color = Muted2)
        }
        DeliveryState.QUEUED -> {
            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(10.dp), tint = Muted2)
            Text("Queued", fontSize = 9.sp, color = Muted2)
        }
        DeliveryState.FAILED -> {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(10.dp), tint = RedInk)
            Text("Not sent", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = RedInk)
            Text(
                "Retry",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = RedInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .clickable { state.retryMessage(task.id, message.id) }
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            )
        }
        DeliveryState.DELIVERED -> Text(clockTime(message.at), fontSize = 9.sp, color = Muted2)
    }
}

@Composable
private fun TypingBlock(modelName: String, cloud: Boolean) {
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "typing-alpha"
    )
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(modelName, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Ink)
            if (cloud) CloudChip() else LocalChip("On this phone")
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Surface3)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Muted.copy(alpha = if (index == 1) alpha else alpha * 0.7f))
                )
                if (index < 2) Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (cloud) "Waiting for the cloud model…" else "Generating locally…", fontSize = 10.sp, color = Muted)
        }
    }
}

@Composable
private fun PlanCard(task: Task) {
    val plan = task.plan ?: return
    val done = plan.count { it.state == PlanState.DONE }
    AdCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Progress", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
            Text("$done of ${plan.size}", fontSize = 9.5.sp, color = Muted2)
        }
        Spacer(Modifier.height(9.dp))
        plan.forEach { step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (step.state) {
                    PlanState.DONE -> Icons.Outlined.CheckCircle
                    PlanState.ACTIVE -> Icons.Outlined.Sync
                    PlanState.FAILED -> Icons.Outlined.ErrorOutline
                    PlanState.TODO -> Icons.Outlined.RadioButtonUnchecked
                }
                val tint = when (step.state) {
                    PlanState.DONE -> GreenInk
                    PlanState.ACTIVE -> BlueInk
                    PlanState.FAILED -> RedInk
                    PlanState.TODO -> androidx.compose.ui.graphics.Color(0xFFC3C8CE)
                }
                Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = tint)
                Spacer(Modifier.width(7.dp))
                Text(
                    step.text,
                    fontSize = 11.sp,
                    color = if (step.state == PlanState.DONE) Muted else InkSoft
                )
            }
            Spacer(Modifier.height(7.dp))
        }
    }
}

@Composable
private fun ActivityCard(state: AgentDeckState, task: Task) {
    if (task.activity.isEmpty()) return
    val open = task.id in state.activityOpen
    val events = if (open) task.activity else task.activity.takeLast(2)
    AdCard(padding = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.toggleActivity(task.id) }
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Timeline, contentDescription = null, modifier = Modifier.size(14.dp), tint = Muted)
            Spacer(Modifier.width(6.dp))
            Text("Activity", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.width(6.dp))
            val hasCloudHistory = task.messages.any { it.cloud || it.provider == ModelProvider.CLOUD } ||
                task.activity.any { it.type == "cloud" || it.type == "cloud-error" }
            val hasLocalHistory = task.messages.any { it.provider == ModelProvider.LOCAL } ||
                task.activity.any { it.type == "local" || it.type == "local-engine" }
            val hasLiveHostHistory = task.activity.any { it.type == "host" || it.type == "host-error" }
            when {
                hasLiveHostHistory -> AdTag("Live host", com.agentdeck.app.ui.theme.GreenSoft, com.agentdeck.app.ui.theme.GreenInk)
                hasLocalHistory && hasCloudHistory -> AdTag("Mixed", GraySoft, GrayInk)
                hasLocalHistory -> AdTag("On device", com.agentdeck.app.ui.theme.BlueSoft, com.agentdeck.app.ui.theme.BlueInk)
                hasCloudHistory -> CloudChip("Cloud")
                else -> AdTag("History", GraySoft, GrayInk)
            }
            Spacer(Modifier.weight(1f))
            Text("${task.activity.size} events", fontSize = 9.sp, color = Muted2)
            Spacer(Modifier.width(3.dp))
            Icon(
                if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = Muted2
            )
        }
        Column(Modifier.padding(start = 13.dp, end = 13.dp, bottom = 11.dp)) {
            events.forEach { event ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        activityIcon(event.type),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp).padding(top = 2.dp),
                        tint = Muted2
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(event.text, fontSize = 10.5.sp, lineHeight = 14.sp, color = InkSoft, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(7.dp))
                    Text(relativeTime(event.at), fontSize = 8.5.sp, color = Muted2)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun activityIcon(type: String): ImageVector = when (type) {
    "read" -> Icons.Outlined.Description
    "edit" -> Icons.Outlined.Edit
    "test" -> Icons.Outlined.Terminal
    "review" -> Icons.Outlined.CheckCircle
    "queue" -> Icons.Outlined.Schedule
    "error" -> Icons.Outlined.ErrorOutline
    "permission" -> Icons.Outlined.Shield
    "connect" -> Icons.Outlined.Dns
    "local" -> Icons.Outlined.PhoneAndroid
    "cloud" -> Icons.Outlined.Cloud
    "cloud-error" -> Icons.Outlined.Warning
    "github" -> Icons.Outlined.Code
    "github-error" -> Icons.Outlined.Warning
    "model" -> Icons.Outlined.Sync
    else -> Icons.Outlined.Timeline
}

@Composable
private fun PermissionCard(state: AgentDeckState, task: Task, request: PermissionRequest) {
    AdCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(13.dp), tint = BlueInk)
            Spacer(Modifier.width(5.dp))
            Text(
                "PERMISSION NEEDED",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp,
                color = BlueInk
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(request.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(5.dp))
        Text(request.body, fontSize = 11.5.sp, lineHeight = 17.sp, color = Muted)
        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2)
                .border(1.dp, Line, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(12.dp), tint = Muted)
            Spacer(Modifier.width(6.dp))
            Text(request.command, fontFamily = Mono, fontSize = 10.sp, color = InkSoft)
        }
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdButton(
                "Decline",
                onClick = { state.declinePermission(task.id) },
                modifier = Modifier.weight(1f),
                kind = AdButtonKind.OUTLINE,
                enabled = state.canReachHost()
            )
            AdButton(
                "Allow once",
                onClick = { state.allowPermission(task.id) },
                modifier = Modifier.weight(1f),
                enabled = state.canReachHost()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Approval applies only to this command in this task.", style = AdType.Tiny)
    }
}

@Composable
private fun FailureCard(state: AgentDeckState, task: Task, failure: Failure) {
    AdCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(13.dp), tint = RedInk)
            Spacer(Modifier.width(5.dp))
            Text("TASK STOPPED", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = RedInk)
        }
        Spacer(Modifier.height(6.dp))
        Text(failure.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(failure.summary, fontSize = 11.5.sp, color = Muted)
        Spacer(Modifier.height(9.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(9.dp),
            color = androidx.compose.ui.graphics.Color(0xFFFFF7F6),
            border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF2D4D0))
        ) {
            Text(
                failure.detail,
                modifier = Modifier.padding(10.dp),
                fontFamily = Mono,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = androidx.compose.ui.graphics.Color(0xFF7A2A22)
            )
        }
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdButton(
                "View checks",
                onClick = { state.openChecks(task.id) },
                modifier = Modifier.weight(1f),
                kind = AdButtonKind.OUTLINE
            )
            AdButton(
                "Retry task",
                onClick = { state.retryTask(task.id) },
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Refresh,
                enabled = state.canReachHost()
            )
        }
        if (!state.canReachHost()) {
            Spacer(Modifier.height(7.dp))
            Text("Reconnect the host before retrying.", style = AdType.Tiny)
        }
    }
}

@Composable
private fun ReadyCard(state: AgentDeckState, task: Task) {
    val summary = summarizeChecks(task.checks)
    val additions = task.changedFiles.sumOf { it.additions }
    val deletions = task.changedFiles.sumOf { it.deletions }
    AdCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(31.dp)
                    .clip(CircleShape)
                    .background(GreenSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = GreenInk)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Ready for review", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(
                    "${task.changedFiles.size} files changed · +$additions −$deletions\n${summary.label}",
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    color = Muted
                )
            }
        }
        Spacer(Modifier.height(11.dp))
        AdButton(
            "Review changes",
            onClick = { state.openTaskForReview(task.id) },
            modifier = Modifier.fillMaxWidth(),
            fillWidth = true
        )
        if (summary.failed) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(12.dp), tint = AmberInk)
                Spacer(Modifier.width(5.dp))
                Text("A check failed. Review it before approving.", fontSize = 10.sp, color = AmberInk)
            }
        }
    }
}

@Composable
private fun DecisionCard(task: Task) {
    val decision = task.decisions.firstOrNull() ?: return
    AdCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(31.dp)
                    .clip(CircleShape)
                    .background(GreenSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = GreenInk)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Approved by you", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(
                    "${fullDate(decision.at)} · ${clockTime(decision.at)}",
                    fontSize = 9.5.sp,
                    color = Muted2
                )
                if (decision.feedback.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(decision.feedback, fontSize = 10.5.sp, lineHeight = 15.sp, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun TaskHostBanner(state: AgentDeckState, task: Task, local: Boolean, cloud: Boolean = false) {
    // Only host-model tasks depend on the optional PC bridge.
    if (state.model(task.modelId)?.isHost != true) return
    if (!state.hostConfig.configured) return
    if (cloud) {
        if (state.canReachHost()) return
        InfoBanner(
            title = "${state.data.host.name} is offline",
            text = "Cloud analysis and GitHub applies still work. Only host-agent tasks need a reconnect.",
            kind = BannerKind.CLOUD,
            modifier = Modifier.padding(bottom = 12.dp),
            action = {
                AdButton("Reconnect", onClick = state::reconnectHost, kind = AdButtonKind.OUTLINE, small = true)
            }
        )
        return
    }
    if (state.canReachHost()) return
    val model = state.model(task.modelId)
    val copy = if (local) {
        when (model?.localState) {
            LocalModelState.READY -> "Local chat is available on this phone. Only host-agent tasks need a reconnect."
            LocalModelState.LOADING -> "The local model is loading. Only host-agent tasks need a reconnect."
            else -> "This local model is ${model?.localState?.label?.lowercase() ?: "not ready"}. Load it in Settings to chat on this phone."
        }
    } else {
        "Host models and execution need a reconnect."
    }
    InfoBanner(
        title = "${state.data.host.name} is offline",
        text = copy,
        kind = if (local) BannerKind.LOCAL else BannerKind.WARNING,
        modifier = Modifier.padding(bottom = 12.dp),
        action = {
            AdButton("Reconnect", onClick = state::reconnectHost, kind = AdButtonKind.OUTLINE, small = true)
        }
    )
}

/** Compact inline notice above the composer input; keeps switching reachable. */
@Composable
private fun ComposerNotice(
    text: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    secondLabel: String? = null,
    onSecond: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AmberSoft)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = AmberInk
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            fontSize = 9.5.sp,
            lineHeight = 13.sp,
            color = AmberInk
        )
        if (actionLabel != null) {
            Text(
                actionLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = BlueInk
            )
        }
        if (secondLabel != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                secondLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(onClick = onSecond)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = BlueInk
            )
        }
    }
}

@Composable
private fun ComposerBar(state: AgentDeckState, task: Task, model: ModelOption?, local: Boolean, cloud: Boolean) {
    val modelState = model?.localState
    if (task.status == TaskStatus.COMPLETED) {
        Surface(Modifier.fillMaxWidth(), color = AdSurface, border = BorderStroke(1.dp, Line)) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(13.dp)) {
                InfoBanner(
                    title = "Task completed",
                    text = "Start a new task to keep going.",
                    kind = BannerKind.DONE,
                    action = {
                        AdButton("New task", onClick = state::openNewTask, kind = AdButtonKind.OUTLINE, small = true)
                    }
                )
            }
        }
        return
    }
    // Blocked senders keep the composer (and its Change control) visible; only
    // sending is disabled, so the user can always switch models from here.
    val localBlocked = local && modelState != LocalModelState.READY
    val cloudBlocked = cloud && !state.cloudConfig.configured
    val hostBlocked = !local && !cloud && !state.canReachHost()

    var text by remember(task.id) { mutableStateOf("") }
    val streamingCloud = state.isCloudStreaming(task.id)
    val streamingHost = state.isHostRunning(task.id)
    val streamingLocal = state.isLocalStreaming(task.id)
    val streaming = streamingCloud || streamingHost || streamingLocal
    val retryCloud = state.canRetryCloudReply(task.id)
    val busy = state.isTaskBusy(task.id)
    val canSend = text.isNotBlank() && !busy && !localBlocked && !cloudBlocked && !hostBlocked
    val switchable = local || cloud
    fun send() {
        if (!canSend) return
        state.sendMessage(task.id, text)
        text = ""
    }

    Surface(Modifier.fillMaxWidth(), color = AdSurface, border = BorderStroke(1.dp, Line)) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            if (retryCloud) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(RedSoft)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = RedInk
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Cloud request failed. The conversation is saved.",
                        modifier = Modifier.weight(1f),
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp,
                        color = RedInk
                    )
                    Text(
                        "Retry",
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { state.retryCloudReply(task.id) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlueInk
                    )
                }
            }
            if (localBlocked) {
                ComposerNotice(
                    text = when {
                        modelState == LocalModelState.LOADING -> "Loading local model…"
                        model == null -> "This task's model is no longer available"
                        else -> "Local model not loaded"
                    },
                    actionLabel = if (model == null) null else "Load",
                    onAction = state::openSettings,
                    secondLabel = "Change model",
                    onSecond = { state.openTaskModels(task.id) }
                )
            }
            if (cloudBlocked) {
                ComposerNotice(
                    text = "Cloud API is not configured",
                    actionLabel = "Set up",
                    onAction = state::openCloudSetup,
                    secondLabel = "Change model",
                    onSecond = { state.openTaskModels(task.id) }
                )
            }
            if (hostBlocked) {
                ComposerNotice(
                    text = "${state.data.host.name} is offline",
                    actionLabel = "Reconnect",
                    onAction = state::reconnectHost
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface2)
                        .border(1.dp, Line, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    if (text.isEmpty()) {
                        Text(
                            when {
                                task.agentMode -> "Instruct the agent…"
                                cloud -> "Ask about the code…"
                                local -> "Message locally…"
                                task.status == TaskStatus.NEEDS_INPUT -> "Answer the agent…"
                                else -> "Message the agent…"
                            },
                            fontSize = 12.5.sp,
                            color = Muted2
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp, color = Ink),
                        cursorBrush = SolidColor(BlueInk)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            when {
                                streaming -> RedSoft
                                canSend -> Ink
                                else -> androidx.compose.ui.graphics.Color(0xFFE2E5E9)
                            }
                        )
                        .clickable(enabled = streaming || canSend) {
                            when {
                                streamingCloud -> state.stopCloudReply(task.id)
                                streamingHost -> state.stopHostRun(task.id)
                                streamingLocal -> state.stopLocalReply(task.id)
                                else -> send()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        streaming -> Icon(
                            Icons.Outlined.Stop,
                            contentDescription = if (streamingHost) "Stop host run" else "Stop generation",
                            modifier = Modifier.size(17.dp),
                            tint = RedInk
                        )
                        busy -> CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = Muted
                        )
                        else -> Icon(
                            Icons.Outlined.Send,
                            contentDescription = "Send message",
                            modifier = Modifier.size(16.dp),
                            tint = if (canSend) AdSurface else androidx.compose.ui.graphics.Color(0xFFA6ACB3)
                        )
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .clickable(enabled = switchable && !busy) { state.openTaskModels(task.id) }
                    .padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    state.taskModelName(task),
                    fontSize = 8.5.sp,
                    color = Muted2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (switchable) {
                    Text(
                        "Change",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (busy) Muted2 else BlueInk
                    )
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Change model",
                        modifier = Modifier.size(11.dp),
                        tint = if (busy) Muted2 else BlueInk
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (local) {
                    Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, modifier = Modifier.size(10.dp), tint = BlueInk)
                    Text("On this phone", fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, color = BlueInk)
                    Text("·", fontSize = 8.5.sp, color = Muted2)
                }
                if (cloud) {
                    Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(10.dp), tint = com.agentdeck.app.ui.theme.VioletInk)
                    Text("Cloud API", fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, color = com.agentdeck.app.ui.theme.VioletInk)
                    Text("·", fontSize = 8.5.sp, color = Muted2)
                }
                Text(if (cloud) "Read-only analysis" else "Standard permissions", fontSize = 8.5.sp, color = Muted2)
            }
            val hint = when {
                streamingHost -> "Host run in progress. Stop to cancel the agent on the host; reported changes stay."
                streamingLocal -> "Generating on this phone. Stop to keep the partial reply; nothing is changed or run."
                streamingCloud -> "Cloud reply streaming. Stop to keep the partial reply; nothing is changed or run."
                cloud -> "Nothing is changed or run here. Apply proposed files from the Changes tab to open a pull request."
                local -> "Runs on this phone. Nothing is changed or run here."
                task.status == TaskStatus.FAILED -> "This task is stopped. Messages are saved; retry the task to resume."
                task.status == TaskStatus.NEEDS_INPUT && task.permissionRequest != null -> "Resolve the permission request above, or reply below."
                else -> "Message the agent on ${state.data.host.name}."
            }
            if (hint.isNotBlank()) {
                Text(hint, fontSize = 9.sp, lineHeight = 12.sp, color = Muted2, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun ChangesContent(
    state: AgentDeckState,
    task: Task,
    project: Project,
    local: Boolean,
    cloud: Boolean,
    modifier: Modifier = Modifier
) {
    val selected = task.changedFiles.firstOrNull { it.id == state.diffFileId }
    if (selected != null) {
        DiffContent(state = state, task = task, project = project, file = selected, modifier = modifier)
        return
    }
    val chatOnly = (local || cloud) && task.changedFiles.isEmpty()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TaskHostBanner(state, task, local, cloud)
        if (cloud) {
            RepoContextSummary(state, task)
            Spacer(Modifier.height(10.dp))
        }
        if (!chatOnly) {
            OverviewCard(state = state, task = task, project = project)
            Spacer(Modifier.height(10.dp))
        }
        if (task.changedFiles.isEmpty()) {
            if (cloud) {
                ProposalCard(state, task)
                Spacer(Modifier.height(10.dp))
            } else {
                AdEmptyState(
                    icon = when {
                        local -> Icons.Outlined.PhoneAndroid
                        else -> Icons.Outlined.Description
                    },
                    title = when {
                        local -> "Local chat only"
                        else -> "No changes yet"
                    },
                    text = when {
                        local -> "This task is scoped to a local explanation. Proposals apply to GitHub from cloud tasks."
                        else -> "Files appear here after a proposal is applied."
                    },
                    action = {
                        AdButton("Back to chat", onClick = { state.selectTaskTab(TaskTab.CHAT) }, kind = AdButtonKind.OUTLINE, small = true)
                    }
                )
            }
        } else {
            task.changedFiles.forEach { file ->
                FileRow(file = file, onClick = { state.selectDiffFile(file.id) })
                Spacer(Modifier.height(6.dp))
            }
        }
        if (!chatOnly) {
            Spacer(Modifier.height(7.dp))
            ReadinessNote(task)
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ProposalCard(state: AgentDeckState, task: Task) {
    val proposals = androidx.compose.runtime.remember(task.messages) { state.proposedFilesFor(task) }
    val hasToken = state.hasGithubWriteToken()
    val applying = state.isApplyingProposal(task.id)
    AdCard {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(14.dp), tint = Muted)
                Spacer(Modifier.width(6.dp))
                Text("Cloud proposal", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.weight(1f))
                if (proposals.isNotEmpty()) {
                    Text("${proposals.size} file${if (proposals.size == 1) "" else "s"}", fontSize = 9.5.sp, color = Muted)
                }
            }
            Spacer(Modifier.height(6.dp))
            if (proposals.isEmpty()) {
                Text(
                    "This cloud task analyses and proposes changes in chat. When a reply includes proposed files, apply them here to commit a branch and open a pull request on GitHub.",
                    style = AdType.Meta,
                    lineHeight = 15.sp
                )
            } else {
                proposals.take(5).forEach { proposal ->
                    Text(
                        proposal.path,
                        fontFamily = Mono,
                        fontSize = 9.5.sp,
                        color = InkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (proposals.size > 5) {
                    Text("+${proposals.size - 5} more file${if (proposals.size - 5 == 1) "" else "s"}", fontSize = 9.5.sp, color = Muted2)
                }
                Spacer(Modifier.height(10.dp))
                AdButton(
                    if (applying) "Applying to GitHub…" else "Apply · branch + pull request",
                    onClick = { state.applyProposal(task.id) },
                    enabled = !applying,
                    fillWidth = true,
                    small = true
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (hasToken) {
                        "Commits these files to a new branch and opens a pull request against ${state.githubConfig.branch.ifBlank { "the default branch" }}. CI checks appear in the Checks tab."
                    } else {
                        "Needs a GitHub token with write access. Add one in Settings → Repository context, then apply."
                    },
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp,
                    color = Muted2
                )
            }
        }
    }
}

@Composable
private fun OverviewCard(state: AgentDeckState, task: Task, project: Project) {
    val summary = summarizeChecks(task.checks)
    val additions = task.changedFiles.sumOf { it.additions }
    val deletions = task.changedFiles.sumOf { it.deletions }
    AdCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(Surface2)
                    .border(1.dp, Line, RoundedCornerShape(7.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.CallSplit, contentDescription = null, modifier = Modifier.size(11.dp), tint = Muted)
                Spacer(Modifier.width(4.dp))
                Text(task.branch, fontFamily = Mono, fontSize = 9.5.sp, color = InkSoft)
            }
            Spacer(Modifier.weight(1f))
            Text("from ${project.defaultBranch}", fontSize = 9.sp, color = Muted2)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${task.changedFiles.size} files changed",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            Text("+$additions", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = GreenInk)
            Spacer(Modifier.width(8.dp))
            Text("−$deletions", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = RedInk)
        }
        Spacer(Modifier.height(9.dp))
        AdDivider()
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(PillShape)
                    .background(summary.tone.bg)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (summary.failed) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = summary.tone.fg
                )
                Spacer(Modifier.width(4.dp))
                Text(summary.label, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = summary.tone.fg)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "View checks",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = BlueInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { state.openChecks(task.id) }
                    .padding(horizontal = 3.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun FileRow(file: ChangedFile, onClick: () -> Unit) {
    val tone = changeTone(file.changeType)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AdSurface,
        border = BorderStroke(1.dp, Line)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(tone.bg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    changeLetter(file.changeType),
                    fontFamily = Mono,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = tone.fg
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.path,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${file.changeType.label}${file.oldPath?.let { " from $it" } ?: ""} · ${file.language}",
                    fontSize = 9.sp,
                    color = Muted2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("+${file.additions}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GreenInk)
            Spacer(Modifier.width(5.dp))
            Text("−${file.deletions}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RedInk)
        }
    }
}

@Composable
private fun ReadinessNote(task: Task) {
    val text = when (task.status) {
        TaskStatus.REVIEW -> "Review additions and deletions, then approve the work or request changes."
        TaskStatus.COMPLETED -> "This work was approved. It stays in history."
        TaskStatus.FAILED -> "This task stopped before finishing. Review the changes, then retry."
        TaskStatus.NEEDS_INPUT -> "Waiting for your response before work continues."
        TaskStatus.PENDING -> "The task is queued. Files will appear when execution starts."
        TaskStatus.IN_PROGRESS -> "The agent is still working. Files may change until it finishes."
    }
    Row(
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(13.dp), tint = Muted2)
        Spacer(Modifier.width(7.dp))
        Text(text, fontSize = 10.sp, lineHeight = 14.sp, color = Muted)
    }
}

@Composable
private fun DiffContent(
    state: AgentDeckState,
    task: Task,
    project: Project,
    file: ChangedFile,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(vScroll)
            .padding(16.dp)
    ) {
        TaskHostBanner(state, task, state.isLocalTask(task), state.isCloudTask(task))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { state.selectDiffFile(null) }
                    .padding(vertical = 3.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(15.dp), tint = InkSoft)
                Spacer(Modifier.width(3.dp))
                Text("All files", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkSoft)
            }
            Spacer(Modifier.weight(1f))
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AdSurface)
                        .border(1.dp, Line, RoundedCornerShape(8.dp))
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        file.path.substringAfterLast("/"),
                        fontFamily = Mono,
                        fontSize = 9.5.sp,
                        color = InkSoft,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(12.dp), tint = Muted)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    task.changedFiles.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.path, fontFamily = Mono, fontSize = 10.sp, color = Ink) },
                            onClick = {
                                expanded = false
                                state.selectDiffFile(candidate.id)
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AdCard(padding = 11.dp) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(changeTone(file.changeType).bg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(changeLetter(file.changeType), fontFamily = Mono, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = changeTone(file.changeType).fg)
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.path, fontFamily = Mono, fontSize = 11.sp, lineHeight = 16.sp, color = Ink)
                    if (file.oldPath != null) {
                        Text("Renamed from ${file.oldPath}", fontSize = 9.sp, color = AmberInk)
                    }
                    Text(
                        "${file.changeType.label} · ${file.language} · +${file.additions} −${file.deletions}",
                        fontSize = 9.sp,
                        color = Muted2
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            AdDivider()
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CallSplit, contentDescription = null, modifier = Modifier.size(11.dp), tint = Muted2)
                Spacer(Modifier.width(5.dp))
                Text("${task.branch} · from ${project.defaultBranch}", fontSize = 9.sp, color = Muted2)
            }
        }
        Spacer(Modifier.height(9.dp))
        if (!file.diffAvailable) {
            AdEmptyState(
                icon = Icons.Outlined.Description,
                title = "Diff unavailable",
                text = file.unavailableReason ?: "The host did not provide a diff for this file.",
                action = {
                    Text(
                        "Reported change: +${file.additions} −${file.deletions}",
                        fontSize = 9.5.sp,
                        color = Muted2
                    )
                }
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                color = AdSurface,
                border = BorderStroke(1.dp, Line)
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val viewport = maxWidth
                    Column(
                        Modifier
                            .horizontalScroll(hScroll)
                            .padding(vertical = 1.dp)
                    ) {
                    DiffHunk(file, viewport)
                    file.diffLines.forEach { line ->
                        val bg = when (line.type) {
                            DiffLineType.ADD -> androidx.compose.ui.graphics.Color(0xFFF3FBF6)
                            DiffLineType.DELETE -> androidx.compose.ui.graphics.Color(0xFFFFF4F2)
                            DiffLineType.CONTEXT -> AdSurface
                        }
                        val sign = when (line.type) {
                            DiffLineType.ADD -> "+"
                            DiffLineType.DELETE -> "−"
                            DiffLineType.CONTEXT -> " "
                        }
                        Row(Modifier.widthIn(min = viewport).background(bg).padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                line.oldNumber?.toString() ?: "",
                                modifier = Modifier.width(30.dp).padding(end = 6.dp),
                                fontFamily = Mono,
                                fontSize = 9.sp,
                                textAlign = TextAlign.End,
                                color = Muted2
                            )
                            Text(
                                line.newNumber?.toString() ?: "",
                                modifier = Modifier.width(30.dp).padding(end = 6.dp),
                                fontFamily = Mono,
                                fontSize = 9.sp,
                                textAlign = TextAlign.End,
                                color = Muted2
                            )
                            Text(
                                sign,
                                modifier = Modifier.width(15.dp),
                                fontFamily = Mono,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = if (line.type == DiffLineType.ADD) GreenInk else if (line.type == DiffLineType.DELETE) RedInk else Muted
                            )
                            Text(
                                if (line.text.isEmpty()) " " else line.text,
                                fontFamily = Mono,
                                fontSize = 10.5.sp,
                                lineHeight = 17.sp,
                                softWrap = false,
                                maxLines = 1,
                                color = when (line.type) {
                                    DiffLineType.ADD -> androidx.compose.ui.graphics.Color(0xFF194A2F)
                                    DiffLineType.DELETE -> androidx.compose.ui.graphics.Color(0xFF7C2B23)
                                    DiffLineType.CONTEXT -> InkSoft
                                }
                            )
                            Spacer(Modifier.width(14.dp))
                        }
                    }
                    }
                }
            }
            Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(11.dp), tint = Muted2)
                Spacer(Modifier.width(4.dp))
                Text("Long lines scroll sideways. This diff is read-only.", fontSize = 9.sp, color = Muted2)
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun DiffHunk(file: ChangedFile, minWidth: Dp) {
    val oldCount = file.diffLines.count { it.oldNumber != null }
    val newCount = file.diffLines.count { it.newNumber != null }
    val firstOld = file.diffLines.firstOrNull { it.oldNumber != null }?.oldNumber ?: 0
    val firstNew = file.diffLines.firstOrNull { it.newNumber != null }?.newNumber ?: 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color(0xFFF5F7F8))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text("@@ −$firstOld,$oldCount +$firstNew,$newCount @@", fontFamily = Mono, fontSize = 9.sp, color = Muted)
    }
}

@Composable
private fun ChecksContent(
    state: AgentDeckState,
    task: Task,
    local: Boolean,
    cloud: Boolean,
    model: ModelOption?,
    modifier: Modifier = Modifier
) {
    val summary = summarizeChecks(task.checks)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TaskHostBanner(state, task, local, cloud)
        if (task.checks.isEmpty()) {
            val applied = task.branch.startsWith("agentdeck/")
            AdEmptyState(
                icon = when {
                    cloud -> Icons.Outlined.Cloud
                    local -> Icons.Outlined.PhoneAndroid
                    else -> Icons.Outlined.Terminal
                },
                title = if (local || cloud) "No checks were run" else "Checks have not run yet",
                text = when {
                    cloud && applied -> "CI on the pull request branch has not reported runs yet. GitHub repositories without workflows show none."
                    cloud -> "Checks appear here after a proposal is applied. CI workflows that the repository already runs (for example GitHub Actions) report into this tab."
                    local -> "This task chats on the phone. Checks come from CI after a cloud proposal is applied to GitHub."
                    else -> "The agent runs the configured checks before asking for review. Nothing has been reported yet."
                },
                action = {
                    AdButton(
                        if (applied) "Refresh CI status" else "Open chat",
                        onClick = {
                            if (applied) state.refreshGithubChecks(task.id)
                            else state.selectTaskTab(TaskTab.CHAT)
                        },
                        kind = AdButtonKind.OUTLINE,
                        small = true
                    )
                }
            )
            Spacer(Modifier.height(18.dp))
            return@Column
        }
        if (cloud && task.branch.startsWith("agentdeck/")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AdButton(
                    "Refresh CI status",
                    onClick = { state.refreshGithubChecks(task.id) },
                    kind = AdButtonKind.OUTLINE,
                    small = true
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = AdSurface,
            border = BorderStroke(1.dp, Line)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(summary.tone.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (summary.failed) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = summary.tone.fg
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(summary.label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        "${task.checks.count { it.status == CheckStatus.PASSED }} passed · " +
                            "${task.checks.count { it.status == CheckStatus.FAILED }} failed · " +
                            "${task.checks.count { it.status == CheckStatus.SKIPPED }} skipped · " +
                            "${task.checks.count { it.status == CheckStatus.NOT_RUN }} not run",
                        fontSize = 9.5.sp,
                        color = Muted
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        task.checks.forEach { check ->
            CheckCard(state = state, task = task, check = check)
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.padding(horizontal = 2.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(13.dp), tint = Muted2)
            Spacer(Modifier.width(7.dp))
            Text(
                "Only checks configured for this project are shown. A skipped or not-run check is not a pass.",
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = Muted
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun CheckCard(state: AgentDeckState, task: Task, check: CheckRun) {
    var expanded by remember(check.id) { mutableStateOf(check.status == CheckStatus.FAILED) }
    val tone = checkTone(check.status)
    val borderColor = if (check.status == CheckStatus.FAILED) {
        androidx.compose.ui.graphics.Color(0xFFF1D0CC)
    } else {
        Line
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AdSurface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(Modifier.padding(11.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(tone.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(checkIcon(check.status), contentDescription = null, modifier = Modifier.size(14.dp), tint = tone.fg)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(check.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    if (check.command.isNotBlank() && check.command != "—") {
                        Text(check.command, fontFamily = Mono, fontSize = 8.5.sp, color = Muted2)
                    }
                }
                CheckPill(check.status)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "${check.summary}${if (check.duration.isNotBlank()) " · ${check.duration}" else ""}",
                fontSize = 10.5.sp,
                color = Muted
            )
            if (check.output.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (check.status == CheckStatus.FAILED) (if (expanded) "Hide error output" else "View error output")
                        else (if (expanded) "Hide output" else "View output"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BlueInk
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = BlueInk
                    )
                }
                if (expanded) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Surface2,
                        border = BorderStroke(1.dp, Line)
                    ) {
                        Text(
                            check.output,
                            modifier = Modifier.padding(9.dp),
                            fontFamily = Mono,
                            fontSize = 9.5.sp,
                            lineHeight = 14.sp,
                            color = InkSoft
                        )
                    }
                }
            }
            if (check.status == CheckStatus.FAILED) {
                Text(
                    "Discuss in chat",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BlueInk,
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { state.selectTaskTab(TaskTab.CHAT) }
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ReviewBar(state: AgentDeckState, task: Task) {
    val summary = summarizeChecks(task.checks)
    val online = state.canReachHost()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AdSurface,
        border = BorderStroke(1.dp, Line)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${task.changedFiles.size} files · ${summary.label}",
                    fontSize = 9.sp,
                    color = Muted2,
                    modifier = Modifier.weight(1f)
                )
                if (!online) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(11.dp), tint = AmberInk)
                    Spacer(Modifier.width(3.dp))
                    Text("Offline", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = AmberInk)
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdButton(
                    "Request changes",
                    onClick = { state.openRequestChanges(task.id) },
                    modifier = Modifier.weight(1f),
                    kind = AdButtonKind.OUTLINE,
                    enabled = online
                )
                AdButton(
                    "Approve",
                    onClick = { state.openApprove(task.id) },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Check,
                    enabled = online
                )
            }
            if (!online) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "Reconnect the host to record a decision.",
                    fontSize = 9.sp,
                    color = Muted2,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun chatDayLabel(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    return if (date == LocalDate.now()) {
        "TODAY"
    } else {
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(date).uppercase()
    }
}
