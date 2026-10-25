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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentdeck.app.data.TaskKind
import com.agentdeck.app.data.AgentDeckState
import com.agentdeck.app.data.LocalModelState
import com.agentdeck.app.data.ModelOption
import com.agentdeck.app.data.Project
import com.agentdeck.app.ui.theme.Surface as AdSurface
import com.agentdeck.app.ui.theme.AmberInk
import com.agentdeck.app.ui.theme.AmberSoft
import com.agentdeck.app.ui.theme.BlueInk
import com.agentdeck.app.ui.theme.BlueSoft
import com.agentdeck.app.ui.theme.GraySoft
import com.agentdeck.app.ui.theme.Ink
import com.agentdeck.app.ui.theme.Line
import com.agentdeck.app.ui.theme.Muted
import com.agentdeck.app.ui.theme.Muted2
import com.agentdeck.app.ui.theme.RedInk
import com.agentdeck.app.ui.theme.RedSoft
import com.agentdeck.app.ui.theme.Surface2
import com.agentdeck.app.ui.theme.VioletInk
import com.agentdeck.app.ui.theme.VioletSoft

@Composable
fun NewTaskScreen(state: AgentDeckState) {
    val draft = state.newTaskDraft
    val project = state.project(draft.projectId) ?: state.activeProject
    val selectedModel = state.model(draft.modelId)
    val local = selectedModel?.isLocal == true
    val cloud = selectedModel?.isCloud == true
    val ready = selectedModel?.isReady == true
    val online = state.canReachHost()

    Column(Modifier.fillMaxSize().background(Surface2)) {
        AdBackTopBar(
            title = "New task",
            subtitle = "Give the agent a clear goal.",
            onBack = state::back
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!online && selectedModel?.isHost == true) {
                InfoBanner(
                    title = "Host offline",
                    text = "You can prepare this task; it cannot start until ${state.data.host.name} reconnects.",
                    kind = BannerKind.WARNING,
                    action = {
                        AdButton("Reconnect", onClick = state::reconnectHost, kind = AdButtonKind.OUTLINE, small = true)
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ModeCard(
                    title = "Chat",
                    subtitle = "Ask, research, web, memory",
                    selected = draft.taskKind == TaskKind.CHAT,
                    onClick = { state.updateDraftTaskKind(TaskKind.CHAT) },
                    modifier = Modifier.weight(1f)
                )
                ModeCard(
                    title = "Coding",
                    subtitle = "Workspace, files, git, shell",
                    selected = draft.taskKind == TaskKind.CODING,
                    onClick = { state.updateDraftTaskKind(TaskKind.CODING) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))

            FormLabel("Task")
            AdTextField(
                value = draft.title,
                onValueChange = state::updateDraftTitle,
                placeholder = "What should the agent do?",
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))
            FormLabel("Instructions", optional = true)
            AdTextField(
                value = draft.prompt,
                onValueChange = state::updateDraftPrompt,
                placeholder = "Add context, constraints, and how the work should be verified.",
                minHeight = 104.dp
            )
            Text(
                if (cloud) {
                    "A title or instructions are required. This task is read-only analysis."
                } else {
                    "A title or instructions are required. New tasks start on a fresh branch."
                },
                style = AdType.Tiny,
                modifier = Modifier.padding(top = 6.dp)
            )

            if (draft.taskKind == TaskKind.CODING) {
                Spacer(Modifier.height(12.dp))
                FormLabel("Project")
                ProjectDropdown(
                    project = project,
                    projects = state.data.projects,
                    onSelect = state::updateDraftProject
                )
            }

            Spacer(Modifier.height(12.dp))
            FormLabel("Model")
            ModelSelectButton(
                model = selectedModel,
                onClick = state::openModelSheet
            )
            Text(
                when {
                    selectedModel == null -> "Choose a model."
                    local && !ready -> "This model is ${selectedModel.localState?.label?.lowercase() ?: "not ready"}. Load it in Settings before creating the task."
                    cloud && !ready -> "Connect a cloud API in Settings before creating this task."
                    cloud -> "Analysis only. Requests go directly from this phone; nothing is changed or run."
                    local -> "Inference runs on this phone. Nothing is changed or run here."
                    else -> "Host model · runs on ${state.data.host.name}."
                },
                style = AdType.Tiny,
                color = if ((local || cloud) && !ready) AmberInk else Muted2,
                modifier = Modifier.padding(top = 6.dp)
            )

            if (cloud) {
                Spacer(Modifier.height(12.dp))
                FormLabel("Repository context")
                CloudContextPicker(state = state, draft = draft, project = project)
            } else {
                Spacer(Modifier.height(12.dp))
                FormLabel("Branch")
                AdTextField(
                    value = draft.branch,
                    onValueChange = state::updateDraftBranch,
                    placeholder = "feature/new-task",
                    mono = true,
                    singleLine = true
                )
                Text(
                    "Based on ${project.defaultBranch} in ${project.name}.",
                    style = AdType.Tiny,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
            ContextCard(
                model = selectedModel,
                hostName = state.data.host.name,
                online = online,
                repoContext = if (cloud && draft.useRepoContext && state.githubConfig.configured) {
                    state.githubConfig.repo + "@" + state.githubConfig.branch.ifBlank { "default" }
                } else null
            )

            if (draft.error != null) {
                Spacer(Modifier.height(12.dp))
                ErrorCard(
                    message = draft.error,
                    kind = draft.errorKind,
                    onReconnect = state::reconnectHost,
                    onManageModels = state::openSettings,
                    onSetupCloud = state::openCloudSetup
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AdSurface,
            border = BorderStroke(1.dp, Line)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) {
                AdButton(
                    text = if (state.creating) "Creating task…" else "Create task",
                    onClick = state::createTask,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.creating,
                    fillWidth = true
                )
                Text(
                    when {
                        cloud -> "Cloud analysis starts now. Proposed files apply to GitHub from the Changes tab."
                        local -> "Local chat starts on this phone. Nothing is changed or run here."
                        else -> "The task is queued immediately. Nothing is pushed or merged."
                    },
                    style = AdType.Tiny,
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FormLabel(text: String, optional: Boolean = false) {
    Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        if (optional) {
            Spacer(Modifier.weight(1f))
            Text("Optional", style = AdType.Tiny)
        }
    }
}

@Composable
private fun ProjectDropdown(project: Project, projects: List<Project>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(SmallShape)
                .background(AdSurface)
                .border(1.dp, Line, SmallShape)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(project.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = Muted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            projects.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.name, fontSize = 12.5.sp, color = Ink) },
                    onClick = {
                        expanded = false
                        onSelect(item.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelSelectButton(model: ModelOption?, onClick: () -> Unit) {
    val local = model?.isLocal == true
    val cloud = model?.isCloud == true
    val ready = model?.isReady == true
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = SmallShape,
        color = AdSurface,
        border = BorderStroke(
            1.dp,
            if ((local || cloud) && !ready) androidx.compose.ui.graphics.Color(0xFFEAD7AC) else Line
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                        cloud -> VioletInk
                        else -> Muted
                    }
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    model?.name ?: "Choose a model",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when {
                        model == null -> "No model selected"
                        local -> "${model.meta} · ${model.localState?.label ?: "Unavailable"}"
                        cloud -> "${model.meta} · ${if (ready) "ready" else "not configured"}"
                        else -> "${model.providerLabel} · host"
                    },
                    fontSize = 9.5.sp,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (local) {
                LocalChip("On this phone")
                Spacer(Modifier.width(6.dp))
            }
            if (cloud) {
                CloudChip("BYOK")
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(15.dp), tint = Muted2)
        }
    }
}

@Composable
private fun CloudContextPicker(state: AgentDeckState, draft: com.agentdeck.app.data.NewTaskDraft, project: Project) {
    val gh = state.githubConfig
    AdCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(14.dp), tint = com.agentdeck.app.ui.theme.GreenInk)
            Spacer(Modifier.width(6.dp))
            Text("GitHub snapshot", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
            if (gh.configured) {
                AdTag("Read-only", com.agentdeck.app.ui.theme.GreenSoft, com.agentdeck.app.ui.theme.GreenInk)
            } else {
                AdTag("Not connected", GraySoft, Muted)
            }
        }
        Spacer(Modifier.height(7.dp))
        if (gh.configured) {
            Text(
                "${gh.repo} · ${gh.branch.ifBlank { "default branch" }}",
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 10.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { state.updateDraftUseRepoContext(!draft.useRepoContext) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = draft.useRepoContext,
                    onCheckedChange = { state.updateDraftUseRepoContext(it) },
                    colors = CheckboxDefaults.colors(checkedColor = Ink, uncheckedColor = Muted)
                )
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text("Include repository snapshot", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(
                        "README, manifests, and the most relevant source files",
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp,
                        color = Muted2
                    )
                }
            }
            if (state.model(draft.modelId)?.isCloud == true) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { state.updateDraftAgentMode(!draft.agentMode) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = draft.agentMode,
                        onCheckedChange = { state.updateDraftAgentMode(it) },
                        colors = CheckboxDefaults.colors(checkedColor = Ink, uncheckedColor = Muted)
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Run as agent", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(
                            "Reads and edits files in the project workspace, runs shell commands and git — every write needs your approval",
                            fontSize = 9.5.sp,
                            lineHeight = 13.sp,
                            color = Muted2
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            val saved = state.instructions.all()
            if (saved.isNotEmpty()) {
                FormLabel("Instructions", optional = true)
                saved.take(6).forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { state.updateDraftInstruction(if (draft.instructionId == item.id) "" else item.id) }
                            .padding(horizontal = 4.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (draft.instructionId == item.id) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (draft.instructionId == item.id) BlueInk else Muted2
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(item.body.take(70), fontSize = 9.sp, lineHeight = 12.sp, color = Muted2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(5.dp))
            Text(
                if (draft.useRepoContext) {
                    "Fetched read-only when the task starts. The selected files are sent to your cloud model."
                } else {
                    "The model will answer without repository files."
                },
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
                color = Muted2
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (gh.tokenSaved) "Private access · token saved" else "Public access · no token",
                    fontSize = 9.sp,
                    color = Muted2,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Manage",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { state.openGitHubSetup() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        } else {
            Text(
                "No repository connected. The model can still review your question and plan work, but it cannot see the codebase. Connect GitHub for file-aware analysis.",
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = Muted
            )
            Spacer(Modifier.height(8.dp))
            AdButton(
                "Connect GitHub",
                onClick = state::openGitHubSetup,
                kind = AdButtonKind.OUTLINE,
                small = true
            )
        }
    }
}

@Composable
private fun ContextCard(model: ModelOption?, hostName: String, online: Boolean, repoContext: String?) {
    val cloud = model?.isCloud == true
    AdCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(14.dp), tint = Muted)
            Spacer(Modifier.width(6.dp))
            Text(if (cloud) "Read-only analysis" else "Standard permissions", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.width(6.dp))
            Text(if (cloud) "No writes · no commands" else "Read/write · run checks", style = AdType.Tiny)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (cloud) {
                "The cloud model can read the selected snapshot and propose patches. It cannot edit files, run commands, run checks, merge, or deploy."
            } else {
                "Installing packages or using the network requires your approval during the task. Merging and deploying are not available."
            },
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            color = Muted
        )
        AdDivider(Modifier.padding(top = 9.dp, bottom = 8.dp))
        ContextLine(
            when {
                model?.isLocal == true -> Icons.Outlined.PhoneAndroid
                cloud -> Icons.Outlined.Cloud
                else -> Icons.Outlined.Dns
            },
            "Inference: ${model?.name ?: "No model selected"}${
                when {
                    model?.isLocal == true -> " · this phone"
                    cloud -> " · cloud API"
                    else -> " · host"
                }
            }"
        )
        if (repoContext != null) {
            Spacer(Modifier.height(6.dp))
            ContextLine(Icons.Outlined.Code, "Repository: $repoContext · read-only")
        }
        Spacer(Modifier.height(6.dp))
        ContextLine(
            Icons.Outlined.Terminal,
            if (cloud) {
                "Execution: $hostName · ${if (online) "connected, used only for changes" else "offline, not needed for analysis"}"
            } else {
                "Execution: $hostName · ${if (online) "connected" else "offline"}"
            }
        )
        if (model?.isLocal == true || cloud) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(12.dp), tint = if (cloud) VioletInk else BlueInk)
                Spacer(Modifier.width(5.dp))
                Text(
                    if (cloud) {
                        "Cloud analysis is not execution. Any change must be applied and verified by the host."
                    } else {
                        "Local chat stays on this phone. The host is only used for file changes and checks."
                    },
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp,
                    color = Muted
                )
            }
        }
    }
}

@Composable
private fun ContextLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = Muted2)
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 9.5.sp, color = Muted2, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ErrorCard(
    message: String,
    kind: String?,
    onReconnect: () -> Unit,
    onManageModels: () -> Unit,
    onSetupCloud: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = RedSoft,
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF6C9C4))
    ) {
        Column(Modifier.padding(11.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(15.dp), tint = RedInk)
                Spacer(Modifier.width(8.dp))
                Text(message, fontSize = 11.sp, lineHeight = 16.sp, color = RedInk, modifier = Modifier.weight(1f))
            }
            when (kind) {
                "offline" -> Text(
                    "Reconnect host",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedInk,
                    modifier = Modifier
                        .padding(top = 6.dp, start = 23.dp)
                        .clickable(onClick = onReconnect)
                )
                "model" -> Text(
                    "Manage models",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedInk,
                    modifier = Modifier
                        .padding(top = 6.dp, start = 23.dp)
                        .clickable(onClick = onManageModels)
                )
                "cloud" -> Text(
                    "Set up cloud API",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedInk,
                    modifier = Modifier
                        .padding(top = 6.dp, start = 23.dp)
                        .clickable(onClick = onSetupCloud)
                )
            }
        }
    }
}


@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) BlueSoft else Surface2)
            .border(1.dp, if (selected) BlueInk else Line, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) BlueInk else Ink
        )
        Spacer(Modifier.height(2.dp))
        Text(subtitle, fontSize = 8.5.sp, lineHeight = 11.sp, color = Muted2)
    }
}
