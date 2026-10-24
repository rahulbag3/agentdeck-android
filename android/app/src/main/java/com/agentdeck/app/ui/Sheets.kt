package com.agentdeck.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentdeck.app.data.AgentDeckState
import com.agentdeck.app.data.AppConfig
import com.agentdeck.app.data.ConfirmKind
import com.agentdeck.app.data.ConnectionTest
import com.agentdeck.app.data.LocalModelState
import com.agentdeck.app.data.ModelOption
import com.agentdeck.app.data.RepoContext
import com.agentdeck.app.data.RootTab
import com.agentdeck.app.data.Sheet
import com.agentdeck.app.data.Task
import com.agentdeck.app.data.TaskStatus
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
import com.agentdeck.app.ui.theme.VioletSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDeckSheetHost(state: AgentDeckState) {
    val sheet = state.sheet ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { state.showSheet(null) },
        sheetState = sheetState,
        containerColor = AdSurface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        modifier = Modifier.fillMaxWidth().widthInMax()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 18.dp)
        ) {
            when (sheet) {
                is Sheet.Projects -> ProjectsSheet(state)
                is Sheet.Models -> ModelsSheet(state)
                is Sheet.DemoInfo -> DemoInfoSheet(state)
                is Sheet.CloudSetup -> CloudSetupSheet(state)
                is Sheet.GitHubSetup -> GitHubSetupSheet(state)
                is Sheet.HostSetup -> HostSetupSheet(state)
                is Sheet.RepoContextSheet -> RepoContextSheet(state, sheet.taskId)
                is Sheet.TaskModels -> TaskModelsSheet(state, sheet.taskId)
                is Sheet.Approve -> ApproveSheet(state, sheet.taskId)
                is Sheet.RequestChanges -> RequestChangesSheet(state, sheet.taskId)
                is Sheet.Confirm -> ConfirmSheet(state, sheet.kind)
            }
        }
    }
}

private fun Modifier.widthInMax(): Modifier = this.then(Modifier.widthIn(max = 430.dp))

@Composable
private fun ProjectsSheet(state: AgentDeckState) {
    SheetHeader(
        title = "Switch project",
        subtitle = "Tasks are scoped to one project at a time.",
        onClose = { state.showSheet(null) }
    )
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Surface2)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Dns, contentDescription = null, modifier = Modifier.size(13.dp), tint = Muted)
        Spacer(Modifier.width(5.dp))
        Text(state.data.host.name, fontSize = 10.sp, color = Muted)
        Spacer(Modifier.width(5.dp))
        Box(
            Modifier
                .size(5.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (state.data.host.connected) GreenInk else AmberInk)
        )
        Spacer(Modifier.width(4.dp))
        Text(if (state.data.host.connected) "Connected" else "Offline", fontSize = 10.sp, color = Muted)
    }
    Spacer(Modifier.height(10.dp))
    state.data.projects.forEach { project ->
        val active = project.id == state.data.settings.activeProjectId
        val tasks = state.tasksFor(project.id)
        val open = tasks.count { it.status != TaskStatus.COMPLETED }
        Surface(
            onClick = { state.switchProject(project.id) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            color = if (active) androidx.compose.ui.graphics.Color(0xFFFBFCFF) else AdSurface,
            border = BorderStroke(1.dp, if (active) androidx.compose.ui.graphics.Color(0xFFC5D5F6) else Line)
        ) {
            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(35.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Surface2),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(17.dp), tint = Ink)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(project.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        "${project.repo} · ${project.defaultBranch}",
                        fontSize = 9.5.sp,
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "$open open task${if (open == 1) "" else "s"}",
                        fontSize = 9.5.sp,
                        color = Muted2
                    )
                }
                if (active) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(BlueSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = "Active", modifier = Modifier.size(12.dp), tint = BlueInk)
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
    }
    Spacer(Modifier.height(4.dp))
    AdButton(
        "Manage projects",
        onClick = {
            state.showSheet(null)
            state.selectRoot(RootTab.PROJECTS)
        },
        kind = AdButtonKind.OUTLINE,
        fillWidth = true
    )
}

@Composable
private fun ModelsSheet(state: AgentDeckState) {
    val draft = state.newTaskDraft
    SheetHeader(
        title = "Choose a model",
        subtitle = "Cloud and local chat work without the host. Execution stays with the host.",
        onClose = { state.showSheet(null) }
    )
    Spacer(Modifier.height(14.dp))
    SectionRow("CLOUD API") {
        if (state.cloudConfig.configured) CloudChip() else PreviewTag("BYOK")
    }
    Spacer(Modifier.height(7.dp))
    state.cloudModels().forEach { model ->
        ModelSheetRow(
            model = model,
            selected = draft.modelId == model.id,
            onSelect = { state.selectDraftModel(model.id) }
        )
    }
    if (!state.cloudConfig.configured) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Use your own OpenAI-compatible endpoint and key. Requests go directly from this phone.",
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
            color = Muted2
        )
        Spacer(Modifier.height(7.dp))
        AdButton(
            "Set up cloud API",
            onClick = state::openCloudSetup,
            kind = AdButtonKind.OUTLINE,
            fillWidth = true,
            small = true
        )
    }
    Spacer(Modifier.height(12.dp))
    SectionRow("ON THIS DEVICE") {
        PreviewTag()
    }
    Spacer(Modifier.height(7.dp))
    state.localModels().forEach { model ->
        ModelSheetRow(
            model = model,
            selected = draft.modelId == model.id,
            onSelect = { state.selectDraftModel(model.id) }
        )
        Spacer(Modifier.height(6.dp))
    }
    Spacer(Modifier.height(10.dp))
    SectionRow("HOST MODELS") {}
    Spacer(Modifier.height(7.dp))
    state.hostModels().forEach { model ->
        ModelSheetRow(
            model = model,
            selected = draft.modelId == model.id,
            onSelect = { state.selectDraftModel(model.id) }
        )
        Spacer(Modifier.height(6.dp))
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Cloud replies are real when configured; nothing is written, run, or merged. On-device inference is still simulated. Execution uses the host.",
        fontSize = 9.5.sp,
        lineHeight = 14.sp,
        color = Muted2
    )
    Spacer(Modifier.height(12.dp))
    AdButton(
        "Manage models & connections",
        onClick = {
            state.showSheet(null)
            state.openSettings()
        },
        kind = AdButtonKind.OUTLINE,
        fillWidth = true
    )
}

@Composable
private fun SectionRow(label: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.7.sp,
            color = Muted
        )
        Spacer(Modifier.width(6.dp))
        trailing()
    }
}

@Composable
private fun ModelSheetRow(
    model: ModelOption,
    selected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean = true
) {
    val local = model.isLocal
    val cloud = model.isCloud
    val selectable = model.isReady && enabled
    val tone = when {
        local -> modelStateTone(model.localState)
        cloud && selectable -> AdTone(VioletSoft, com.agentdeck.app.ui.theme.VioletInk)
        else -> AdTone(GraySoft, GrayInk)
    }
    Surface(
        onClick = onSelect,
        enabled = selectable,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) androidx.compose.ui.graphics.Color(0xFFFBFCFF) else AdSurface,
        border = BorderStroke(1.dp, if (selected) androidx.compose.ui.graphics.Color(0xFFC5D5F6) else Line)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        when {
                            local -> BlueSoft
                            cloud -> VioletSoft
                            else -> GraySoft
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        local -> Icons.Outlined.PhoneAndroid
                        cloud -> Icons.Outlined.Cloud
                        else -> Icons.Outlined.Dns
                    },
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = when {
                        local -> BlueInk
                        cloud -> com.agentdeck.app.ui.theme.VioletInk
                        else -> GrayInk
                    }
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    model.name,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectable) Ink else Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    model.meta,
                    fontSize = 9.sp,
                    color = Muted2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        local -> Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(tone.bg)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(model.localState?.label ?: "Unavailable", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = tone.fg)
                        }
                        cloud -> Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(tone.bg)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                if (selectable) "BYOK · analysis" else "Not configured",
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = tone.fg
                            )
                        }
                        else -> Text("Host", fontSize = 7.5.sp, color = GrayInk)
                    }
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(BlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = "Selected", modifier = Modifier.size(12.dp), tint = BlueInk)
                }
            }
        }
    }
}

@Composable
private fun DemoInfoSheet(state: AgentDeckState) {
    SheetHeader(
        title = "How this works",
        subtitle = "What is real, and what is simulated",
        onClose = { state.showSheet(null) }
    )
    Spacer(Modifier.height(14.dp))
    DemoPoint(Icons.Outlined.Cloud, VioletSoft, com.agentdeck.app.ui.theme.VioletInk, "Cloud BYOK", "When you connect an API, replies are real and go straight to your endpoint. The model can read a GitHub snapshot and propose changes, but cannot write or run anything.")
    Spacer(Modifier.height(12.dp))
    DemoPoint(Icons.Outlined.Code, GreenSoft, GreenInk, "GitHub context", "Read-only snapshots of selected files. Public repos work without a token; private repos can use a read-only token stored on this phone.")
    Spacer(Modifier.height(12.dp))
    DemoPoint(Icons.Outlined.Dns, BlueSoft, BlueInk, "Simulated host", "Studio Mac is simulated: no machine is contacted and no host code is executed. Host replies, diffs, and checks are generated locally.")
    Spacer(Modifier.height(12.dp))
    DemoPoint(Icons.Outlined.PhoneAndroid, GreenSoft, GreenInk, "On-device preview", "Local model states and replies are simulated. An on-device inference engine is not bundled yet.")
    Spacer(Modifier.height(12.dp))
    DemoPoint(Icons.Outlined.Shield, AmberSoft, AmberInk, "Approvals are local", "Approving records a decision in the workspace on this phone. Nothing is merged, pushed, or deployed.")
    Spacer(Modifier.height(12.dp))
    Text("Tasks and settings are stored on this phone. Backups never include API keys or tokens.", style = AdType.Tiny)
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdButton(
            "Close",
            onClick = { state.showSheet(null) },
            modifier = Modifier.weight(1f),
            kind = AdButtonKind.OUTLINE
        )
        AdButton(
            "Dismiss notice",
            onClick = {
                state.dismissDemoNotice()
                state.showSheet(null)
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DemoPoint(icon: ImageVector, bg: Color, fg: Color, title: String, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(31.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = fg)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(text, fontSize = 11.sp, lineHeight = 16.sp, color = Muted)
        }
    }
}

@Composable
private fun TaskModelsSheet(state: AgentDeckState, taskId: String) {
    val task = state.task(taskId) ?: return
    val busy = state.isTaskBusy(taskId)
    val cloudActive = state.isCloudTask(task)
    val configured = state.cloudConfig.configured
    var cloudModel by remember(task.id) {
        mutableStateOf(task.cloudModel?.takeIf { it.isNotBlank() } ?: state.cloudConfig.model)
    }
    var error by remember { mutableStateOf<String?>(null) }
    val currentCloudName = task.cloudModel ?: state.cloudConfig.model

    SheetHeader(
        title = "Change model",
        subtitle = "Applies to the next messages. Earlier replies keep the model that produced them.",
        onClose = { state.showSheet(null) }
    )
    if (busy) {
        Spacer(Modifier.height(10.dp))
        InfoBanner(
            title = "Reply in progress",
            text = "Wait for the current request to finish before changing models.",
            kind = BannerKind.WARNING
        )
    }
    Spacer(Modifier.height(14.dp))
    SectionRow("CLOUD API") {
        if (cloudActive) CloudChip("Current") else if (configured) CloudChip() else PreviewTag("BYOK")
    }
    Spacer(Modifier.height(7.dp))
    if (configured) {
        Text(
            "${com.agentdeck.app.data.CloudClient.providerLabel(state.cloudConfig.baseUrl)} · same endpoint and key as Settings",
            fontSize = 9.5.sp,
            lineHeight = 13.sp,
            color = Muted2
        )
        Spacer(Modifier.height(8.dp))
        SheetFormLabel("Model for this conversation")
        AdTextField(
            value = cloudModel,
            onValueChange = { cloudModel = it; error = null },
            placeholder = "gpt-4.1-mini",
            mono = true,
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        AdButton(
            if (cloudActive && cloudModel.trim() == currentCloudName) "Using this model" else "Switch to cloud model",
            onClick = {
                val failure = state.switchTaskToCloud(taskId, cloudModel)
                if (failure == null) state.showSheet(null) else error = failure
            },
            kind = AdButtonKind.PRIMARY,
            fillWidth = true,
            enabled = !busy && cloudModel.isNotBlank() &&
                !(cloudActive && cloudModel.trim() == currentCloudName)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Read-only analysis. The endpoint and key stay the same; nothing is changed or run.",
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
            color = Muted2
        )
        if (task.repoContext == null) {
            Spacer(Modifier.height(5.dp))
            Text(
                "No repository snapshot is attached to this task. Start a new task to include GitHub context, or continue without it.",
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
                color = Muted2
            )
        }
    } else {
        Text(
            "Connect a cloud API to switch this conversation to a cloud model.",
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            color = Muted
        )
        Spacer(Modifier.height(8.dp))
        AdButton(
            "Set up cloud API",
            onClick = state::openCloudSetup,
            kind = AdButtonKind.OUTLINE,
            fillWidth = true,
            small = true
        )
    }
    if (error != null) {
        Spacer(Modifier.height(8.dp))
        Text(error!!, fontSize = 10.5.sp, lineHeight = 15.sp, color = RedInk)
    }
    Spacer(Modifier.height(14.dp))
    SectionRow("ON THIS DEVICE") { PreviewTag() }
    Spacer(Modifier.height(7.dp))
    state.localModels().forEach { localModel ->
        ModelSheetRow(
            model = localModel,
            selected = state.isLocalTask(task) && task.modelId == localModel.id,
            onSelect = {
                val failure = state.switchTaskToLocal(taskId, localModel.id)
                if (failure == null) state.showSheet(null) else error = failure
            },
            enabled = !busy
        )
        Spacer(Modifier.height(6.dp))
    }
    Spacer(Modifier.height(2.dp))
    Text(
        "Local inference is simulated and does not read the GitHub snapshot. Switching back to cloud keeps the snapshot attached to this task.",
        fontSize = 9.5.sp,
        lineHeight = 14.sp,
        color = Muted2
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "Host execution models stay with task creation. Start a new task to run on ${state.data.host.name}.",
        fontSize = 9.5.sp,
        lineHeight = 14.sp,
        color = Muted2
    )
    Spacer(Modifier.height(12.dp))
    AdButton(
        "Done",
        onClick = { state.showSheet(null) },
        kind = AdButtonKind.OUTLINE,
        fillWidth = true
    )
}

@Composable
private fun CloudSetupSheet(state: AgentDeckState) {
    val cfg = state.cloudConfig
    var baseUrl by remember { mutableStateOf(cfg.baseUrl.ifBlank { AppConfig.DEFAULT_CLOUD_BASE_URL }) }
    var model by remember { mutableStateOf(cfg.model) }
    var apiKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    SheetHeader(
        title = "Cloud API",
        subtitle = "Bring your own OpenAI-compatible endpoint. Requests go directly from this phone.",
        onClose = { state.showSheet(null) }
    )
    Spacer(Modifier.height(12.dp))
    if (cfg.keySaved) {
        InfoBanner(
            title = "Key saved on this device",
            text = "Encrypted with Android Keystore. Enter a new key only when rotating it.",
            kind = BannerKind.DONE
        )
        Spacer(Modifier.height(12.dp))
    }

    SheetFormLabel("Base URL")
    AdTextField(
        value = baseUrl,
        onValueChange = { baseUrl = it; error = null },
        placeholder = "https://api.openai.com/v1",
        mono = true,
        singleLine = true
    )
    Spacer(Modifier.height(11.dp))
    SheetFormLabel("Model")
    AdTextField(
        value = model,
        onValueChange = { model = it; error = null },
        placeholder = "gpt-4.1-mini",
        mono = true,
        singleLine = true
    )
    Spacer(Modifier.height(11.dp))
    SheetFormLabel("API key")
    AdTextField(
        value = apiKey,
        onValueChange = { apiKey = it; error = null },
        placeholder = if (cfg.keySaved) "Saved · enter a new key to replace" else "sk-…",
        secret = true,
        singleLine = true
    )
    Spacer(Modifier.height(8.dp))
    TestFeedback(state.cloudTest)
    if (error != null) {
        Spacer(Modifier.height(8.dp))
        Text(error!!, fontSize = 10.5.sp, lineHeight = 15.sp, color = RedInk)
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "The key is encrypted with Android Keystore and never written to tasks or logs. Your prompts and selected repository files are sent to this endpoint, and requests use your provider quota. Nothing is written to your repo.",
        fontSize = 9.5.sp,
        lineHeight = 14.sp,
        color = Muted2
    )
    Spacer(Modifier.height(13.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdButton(
            if (state.cloudTest is ConnectionTest.Testing) "Testing…" else "Test",
            onClick = {
                error = null
                state.testCloudConnection(baseUrl, model, apiKey)
            },
            modifier = Modifier.weight(1f),
            kind = AdButtonKind.OUTLINE,
            enabled = state.cloudTest !is ConnectionTest.Testing
        )
        AdButton(
            "Save",
            onClick = {
                val failure = state.saveCloudConfig(baseUrl, model, apiKey)
                error = failure
                if (failure == null) apiKey = ""
            },
            modifier = Modifier.weight(1f),
            enabled = baseUrl.isNotBlank() && model.isNotBlank() && (apiKey.isNotBlank() || cfg.keySaved)
        )
    }
    if (cfg.configured) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { state.showSheet(Sheet.Confirm(ConfirmKind.REMOVE_CLOUD)) }
                .padding(vertical = 8.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = RedInk)
            Spacer(Modifier.width(6.dp))
            Text("Remove cloud API from this device", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RedInk)
        }
    }
}

@Composable
private fun HostSetupSheet(state: AgentDeckState) {
    val cfg = state.hostConfig
    var baseUrl by remember { mutableStateOf(cfg.baseUrl.ifBlank { "http://192.168.1.10:8787" }) }
    var token by remember { mutableStateOf("") }
    var allowInsecure by remember { mutableStateOf(cfg.allowInsecureLan) }
    var error by remember { mutableStateOf<String?>(null) }

    SheetHeader(
        title = "Execution host",
        subtitle = "Run tasks on your computer with the AgentDeck host agent.",
        onClose = { state.showSheet(null) }
    )
    Spacer(Modifier.height(12.dp))
    if (cfg.tokenSaved) {
        InfoBanner(
            title = "Host token saved",
            text = "Encrypted with Android Keystore. Enter a new token only when rotating it.",
            kind = BannerKind.DONE
        )
        Spacer(Modifier.height(12.dp))
    }

    SheetFormLabel("Host URL")
    AdTextField(
        value = baseUrl,
        onValueChange = { baseUrl = it; error = null },
        placeholder = "http://192.168.1.10:8787",
        mono = true,
        singleLine = true
    )
    Spacer(Modifier.height(11.dp))
    SheetFormLabel("Access token")
    AdTextField(
        value = token,
        onValueChange = { token = it; error = null },
        placeholder = if (cfg.tokenSaved) "Saved · enter a new token to replace" else "Printed by the host on startup",
        secret = true,
        singleLine = true
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable { allowInsecure = !allowInsecure }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Allow unencrypted LAN", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(
                "Needed for http:// hosts on a trusted network. Prefer HTTPS or a tunnel.",
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = Muted
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = allowInsecure, onCheckedChange = { allowInsecure = it; error = null })
    }
    Spacer(Modifier.height(8.dp))
    TestFeedback(state.hostTest)
    if (error != null) {
        Spacer(Modifier.height(8.dp))
        Text(error!!, fontSize = 10.5.sp, lineHeight = 15.sp, color = RedInk)
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "The host runs your configured agent CLI in a project folder and streams output, diffs, and checks back. The app never sends arbitrary commands to your computer.",
        fontSize = 9.5.sp,
        lineHeight = 14.sp,
        color = Muted2
    )
    Spacer(Modifier.height(13.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdButton(
            if (state.hostTest is ConnectionTest.Testing) "Testing…" else "Test",
            onClick = {
                error = null
                state.testHostConnection(baseUrl, token, allowInsecure)
            },
            modifier = Modifier.weight(1f),
            kind = AdButtonKind.OUTLINE,
            enabled = state.hostTest !is ConnectionTest.Testing
        )
        AdButton(
            "Save & connect",
            onClick = {
                error = state.saveHostConfig(baseUrl, token, allowInsecure)
                if (error == null) token = ""
            },
            modifier = Modifier.weight(1f),
            enabled = baseUrl.isNotBlank() && (token.isNotBlank() || cfg.tokenSaved)
        )
    }
    if (cfg.configured) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { state.showSheet(Sheet.Confirm(ConfirmKind.REMOVE_HOST)) }
                .padding(vertical = 8.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = RedInk)
            Spacer(Modifier.width(6.dp))
            Text("Remove host from this device", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RedInk)
        }
    }
}

@Composable
private fun GitHubSetupSheet(state: AgentDeckState) {
    val cfg = state.githubConfig
    var repo by remember { mutableStateOf(cfg.repo) }
    var branch by remember { mutableStateOf(cfg.branch) }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    SheetHeader(
        title = "GitHub context",
        subtitle = "Read-only repository snapshots for code analysis. Nothing is written to GitHub.",
        onClose = { state.showSheet(null) }
    )
    Spacer(Modifier.height(12.dp))
    if (cfg.tokenSaved) {
        InfoBanner(
            title = "Read-only token saved",
            text = "Encrypted with Android Keystore. Enter a new token only when rotating it.",
            kind = BannerKind.DONE
        )
        Spacer(Modifier.height(12.dp))
    }

    SheetFormLabel("Repository")
    AdTextField(
        value = repo,
        onValueChange = { repo = it; error = null },
        placeholder = "octocat/Hello-World",
        mono = true,
        singleLine = true
    )
    Spacer(Modifier.height(11.dp))
    SheetFormLabel("Branch", optional = true)
    AdTextField(
        value = branch,
        onValueChange = { branch = it; error = null },
        placeholder = "Default branch",
        mono = true,
        singleLine = true
    )
    Spacer(Modifier.height(11.dp))
    SheetFormLabel("Token", optional = true)
    AdTextField(
        value = token,
        onValueChange = { token = it; error = null },
        placeholder = if (cfg.tokenSaved) "Saved · enter a new token to replace" else "github_pat_… · optional",
        secret = true,
        singleLine = true
    )
    Spacer(Modifier.height(8.dp))
    TestFeedback(state.githubTest)
    if (error != null) {
        Spacer(Modifier.height(8.dp))
        Text(error!!, fontSize = 10.5.sp, lineHeight = 15.sp, color = RedInk)
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "Public repositories work without a token, but unauthenticated requests are limited to 60 per hour. For private repositories or higher limits, use a fine-grained token with Contents: Read and Metadata: Read only. The token stays on this phone and is never sent to the cloud model.",
        fontSize = 9.5.sp,
        lineHeight = 14.sp,
        color = Muted2
    )
    Spacer(Modifier.height(13.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdButton(
            if (state.githubTest is ConnectionTest.Testing) "Testing…" else "Test",
            onClick = {
                error = null
                state.testGitHubConnection(repo, token)
            },
            modifier = Modifier.weight(1f),
            kind = AdButtonKind.OUTLINE,
            enabled = state.githubTest !is ConnectionTest.Testing
        )
        AdButton(
            "Save",
            onClick = {
                val failure = state.saveGitHubConfig(repo, branch, token)
                error = failure
                if (failure == null) token = ""
            },
            modifier = Modifier.weight(1f),
            enabled = repo.isNotBlank()
        )
    }
    if (cfg.configured) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { state.showSheet(Sheet.Confirm(ConfirmKind.REMOVE_GITHUB)) }
                .padding(vertical = 8.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = RedInk)
            Spacer(Modifier.width(6.dp))
            Text("Remove GitHub connection", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RedInk)
        }
    }
}

@Composable
private fun RepoContextSheet(state: AgentDeckState, taskId: String) {
    val task = state.task(taskId) ?: return
    val context = task.repoContext
    val loading = state.isRepoContextLoading(taskId)

    SheetHeader(
        title = "Repository context",
        subtitle = "Read-only snapshot attached to this task.",
        onClose = { state.showSheet(null) }
    )
    if (context == null) {
        Spacer(Modifier.height(12.dp))
        InfoBanner(
            title = "No repository snapshot",
            text = if (loading) "Fetching repository context from GitHub…" else "This task is running without file contents.",
            kind = BannerKind.WARNING
        )
        Spacer(Modifier.height(12.dp))
        AdButton("Close", onClick = { state.showSheet(null) }, kind = AdButtonKind.OUTLINE, fillWidth = true)
        return
    }
    Spacer(Modifier.height(10.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Surface2)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        SheetKV("Repository", context.repo)
        SheetKV("Branch", context.branch)
        SheetKV("Access", context.visibility)
        SheetKV("Files read", context.files.size.toString())
        SheetKV("Fetched", relativeTime(context.fetchedAt))
    }
    if (context.description.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(context.description, fontSize = 10.5.sp, lineHeight = 15.sp, color = Muted)
    }
    if (context.truncated) {
        Spacer(Modifier.height(8.dp))
        InfoBanner(
            title = "Snapshot truncated",
            text = "Only the most relevant files and paths were kept to stay within the model's request size.",
            kind = BannerKind.WARNING
        )
    }
    Spacer(Modifier.height(12.dp))
    Text("FILES IN PROMPT", style = AdType.Section)
    Spacer(Modifier.height(6.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AdSurface)
            .padding(vertical = 2.dp)
    ) {
        context.files.forEachIndexed { index, file ->
            if (index > 0) AdDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (file.role == "source") Icons.Outlined.Code else Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Muted2
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    file.path,
                    fontFamily = Mono,
                    fontSize = 9.5.sp,
                    color = InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Text(file.role, fontSize = 8.sp, color = Muted2)
                Spacer(Modifier.width(5.dp))
                Text("${file.content.length} chars", fontSize = 8.sp, color = Muted2)
            }
        }
        if (context.files.isEmpty()) {
            Text("No files were loaded.", fontSize = 10.sp, color = Muted2, modifier = Modifier.padding(10.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("REPOSITORY PATHS", style = AdType.Section)
    Spacer(Modifier.height(6.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        context.treeSummary.take(14).forEach { path ->
            Text(path, fontFamily = Mono, fontSize = 8.5.sp, lineHeight = 13.sp, color = Muted)
        }
        if (context.treeSummary.size > 14) {
            Text("+${context.treeSummary.size - 14} more paths", fontSize = 8.5.sp, color = Muted2)
        }
    }
    if (loading) {
        Spacer(Modifier.height(8.dp))
        Text("Refreshing repository context…", fontSize = 10.sp, color = BlueInk)
    }
    Spacer(Modifier.height(13.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdButton(
            "Close",
            onClick = { state.showSheet(null) },
            modifier = Modifier.weight(1f),
            kind = AdButtonKind.OUTLINE
        )
        AdButton(
            "Refresh",
            onClick = { state.refreshRepoContext(taskId) },
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Refresh,
            enabled = !loading && state.githubConfig.configured
        )
    }
}

@Composable
private fun TestFeedback(test: ConnectionTest) {
    when (test) {
        is ConnectionTest.Idle -> Unit
        is ConnectionTest.Testing -> {
            Spacer(Modifier.height(8.dp))
            Text("Testing the connection with one small request…", fontSize = 10.sp, color = BlueInk)
        }
        is ConnectionTest.Success -> {
            Spacer(Modifier.height(8.dp))
            InfoBanner(title = "Connection works", text = test.message, kind = BannerKind.DONE)
        }
        is ConnectionTest.Failure -> {
            Spacer(Modifier.height(8.dp))
            InfoBanner(title = "Connection failed", text = test.message, kind = BannerKind.WARNING)
        }
    }
}

@Composable
private fun SheetFormLabel(text: String, optional: Boolean = false) {
    Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        if (optional) {
            Spacer(Modifier.weight(1f))
            Text("Optional", style = AdType.Tiny)
        }
    }
}

@Composable
private fun ApproveSheet(state: AgentDeckState, taskId: String) {
    val task = state.task(taskId) ?: return
    val summary = summarizeChecks(task.checks)
    val additions = task.changedFiles.sumOf { it.additions }
    val deletions = task.changedFiles.sumOf { it.deletions }
    val online = state.canReachHost()
    var checked by remember(taskId) { mutableStateOf(false) }

    SheetHeader(
        title = "Approve changes?",
        subtitle = "This records your decision in the task history.",
        onClose = { state.showSheet(null) }
    )
    Spacer(Modifier.height(13.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Surface2)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        SheetKV("Task", task.title)
        SheetKV("Branch", "${task.branch} → ${state.project(task.projectId)?.defaultBranch ?: "main"}")
        SheetKV("Changes", "${task.changedFiles.size} files · +$additions −$deletions")
        SheetKV("Checks", summary.label)
    }
    if (summary.failed) {
        Spacer(Modifier.height(10.dp))
        InfoBanner(
            title = "A check failed",
            text = "Approval will not run checks. Review the failure first.",
            kind = BannerKind.WARNING
        )
    }
    val notRun = task.checks.count { it.status == com.agentdeck.app.data.CheckStatus.NOT_RUN }
    if (notRun > 0) {
        Spacer(Modifier.height(8.dp))
        Text("Some checks were skipped or not run. They are not treated as passes.", fontSize = 9.5.sp, color = Muted2)
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "Approving does not merge, push, or deploy. It marks the work as accepted in this demo and unlocks a completed task record.",
        fontSize = 11.5.sp,
        lineHeight = 17.sp,
        color = Muted
    )
    if (!online) {
        Spacer(Modifier.height(10.dp))
        InfoBanner(
            title = "Studio Mac is offline",
            text = "Reconnect before recording a decision.",
            kind = BannerKind.WARNING
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(11.dp))
            .clickable { checked = !checked }
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { checked = it },
            colors = CheckboxDefaults.colors(checkedColor = Ink, uncheckedColor = androidx.compose.ui.graphics.Color(0xFF9AA0A8))
        )
        Spacer(Modifier.width(6.dp))
        Text("I have reviewed the changes and want to approve them.", fontSize = 11.sp, lineHeight = 15.sp, color = Ink)
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdButton(
            "Cancel",
            onClick = { state.showSheet(null) },
            modifier = Modifier.weight(1f),
            kind = AdButtonKind.OUTLINE
        )
        AdButton(
            "Approve work",
            onClick = { state.approveTask(taskId) },
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Check,
            enabled = checked && online
        )
    }
}

@Composable
private fun SheetKV(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, fontSize = 10.5.sp, color = Muted, modifier = Modifier.width(70.dp))
        Text(
            value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
    AdDivider()
}

@Composable
private fun RequestChangesSheet(state: AgentDeckState, taskId: String) {
    val task = state.task(taskId) ?: return
    val online = state.canReachHost()
    var feedback by remember(taskId) { mutableStateOf("") }

    SheetHeader(
        title = "Request changes",
        subtitle = "Feedback reopens the task and is recorded in history.",
        onClose = { state.showSheet(null) }
    )
    Spacer(Modifier.height(13.dp))
    Text("What should change?", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(bottom = 6.dp))
    AdTextField(
        value = feedback,
        onValueChange = { feedback = it },
        placeholder = "Be specific: files, behavior, and how to verify the fix.",
        minHeight = 108.dp
    )
    if (!online) {
        Spacer(Modifier.height(10.dp))
        InfoBanner(
            title = "Studio Mac is offline",
            text = "Reconnect before sending feedback.",
            kind = BannerKind.WARNING
        )
    }
    Spacer(Modifier.height(9.dp))
    Text(
        "The host will pick this up and return the task to review when the changes are ready.",
        fontSize = 9.5.sp,
        lineHeight = 14.sp,
        color = Muted2
    )
    Spacer(Modifier.height(13.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdButton(
            "Cancel",
            onClick = { state.showSheet(null) },
            modifier = Modifier.weight(1f),
            kind = AdButtonKind.OUTLINE
        )
        AdButton(
            "Send feedback",
            onClick = { state.requestChanges(taskId, feedback) },
            modifier = Modifier.weight(1f),
            enabled = feedback.isNotBlank() && online
        )
    }
}

@Composable
private fun ConfirmSheet(state: AgentDeckState, kind: ConfirmKind) {
    val title = when (kind) {
        ConfirmKind.DISCONNECT -> "Disconnect Studio Mac?"
        ConfirmKind.RESET -> "Reset workspace?"
        ConfirmKind.REMOVE_CLOUD -> "Remove cloud API?"
        ConfirmKind.REMOVE_GITHUB -> "Remove GitHub connection?"
        ConfirmKind.REMOVE_HOST -> "Remove this host?"
    }
    val body = when (kind) {
        ConfirmKind.DISCONNECT -> "Running host tasks will stop with a retryable error. Messages cannot be sent until you reconnect. Execution is simulated on this device."
        ConfirmKind.RESET -> "This deletes every local task, message, change, check result, decision, and model state, then restores the sample workspace. It cannot be undone. Cloud and GitHub settings are kept."
        ConfirmKind.REMOVE_CLOUD -> "This deletes the saved API key and endpoint settings from this phone. Existing cloud tasks keep their messages but cannot continue until you connect a cloud API again."
        ConfirmKind.REMOVE_GITHUB -> "This deletes the saved repository settings and token. Existing tasks keep the context snapshot they already fetched."
        ConfirmKind.REMOVE_HOST -> "This deletes the saved host URL and token. Host tasks fall back to simulated execution until you connect a host again."
    }
    val confirmLabel = when (kind) {
        ConfirmKind.DISCONNECT -> "Disconnect"
        ConfirmKind.RESET -> "Reset workspace"
        ConfirmKind.REMOVE_CLOUD -> "Remove cloud API"
        ConfirmKind.REMOVE_GITHUB -> "Remove GitHub"
        ConfirmKind.REMOVE_HOST -> "Remove host"
    }
    SheetHeader(
        title = title,
        onClose = { state.showSheet(null) }
    )
    Spacer(Modifier.height(10.dp))
    Text(body, fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdButton(
            "Cancel",
            onClick = { state.showSheet(null) },
            modifier = Modifier.weight(1f),
            kind = AdButtonKind.OUTLINE
        )
        AdButton(
            confirmLabel,
            onClick = {
                when (kind) {
                    ConfirmKind.DISCONNECT -> state.disconnectHost()
                    ConfirmKind.RESET -> state.resetDemo()
                    ConfirmKind.REMOVE_CLOUD -> state.removeCloudConfig()
                    ConfirmKind.REMOVE_GITHUB -> state.removeGitHubConfig()
                    ConfirmKind.REMOVE_HOST -> state.removeHostConfig()
                }
            },
            modifier = Modifier.weight(1f),
            kind = AdButtonKind.DANGER
        )
    }
}
