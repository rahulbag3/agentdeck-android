package com.agentdeck.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import com.agentdeck.app.data.AgentDeckState
import com.agentdeck.app.data.ConfirmKind
import com.agentdeck.app.data.LocalModelState
import com.agentdeck.app.data.ModelOption
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
import com.agentdeck.app.ui.theme.Line
import com.agentdeck.app.ui.theme.Muted
import com.agentdeck.app.ui.theme.Muted2
import com.agentdeck.app.ui.theme.RedInk
import com.agentdeck.app.ui.theme.RedSoft
import com.agentdeck.app.ui.theme.Surface2
import com.agentdeck.app.ui.theme.VioletInk
import com.agentdeck.app.ui.theme.VioletSoft
import java.util.Locale

@Composable
fun SettingsScreen(state: AgentDeckState) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
    }
    val host = state.data.host
    val online = host.connected
    val settings = state.data.settings
    val project = state.project(settings.defaultProjectId)
    val defaultModel = state.model(settings.defaultModelId)
    val locals = state.localModels()
    val importedGB = locals.sumOf { if (it.localState == LocalModelState.NOT_IMPORTED) 0.0 else (it.sizeGB ?: 0.0) }

    Column(Modifier.fillMaxSize().background(Surface2)) {
        RootTopBar(
            title = "Settings",
            subtitle = "Host, cloud API, GitHub, and on-device models."
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SettingsLabel("Execution host") }
            item {
                val live = state.hostConfig.configured
                AdCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Surface2),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Dns, contentDescription = null, modifier = Modifier.size(18.dp), tint = Ink)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (live) host.name else "No host connected",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink
                            )
                            Text(
                                if (live) "${host.platform} · ${host.transport}"
                                else "Tasks stay simulated until you connect",
                                style = AdType.Tiny
                            )
                        }
                        AdTag(
                            when {
                                !live -> "Not set"
                                online -> "Connected"
                                else -> "Offline"
                            },
                            when {
                                !live -> GraySoft
                                online -> GreenSoft
                                else -> AmberSoft
                            },
                            when {
                                !live -> GrayInk
                                online -> GreenInk
                                else -> AmberInk
                            }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            !live -> "Connect the AgentDeck host agent to run tasks, collect diffs, and execute checks on your computer. Without a host, those steps are simulated."
                            online -> "Live host · last seen ${relativeTime(host.lastSeen)}. Tasks run your configured agent CLI on the host."
                            else -> "Host tasks are stopped and can be retried. On-device chat remains available."
                        },
                        style = AdType.Meta
                    )
                    Spacer(Modifier.height(11.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when {
                            !live -> AdButton("Set up host", onClick = state::openHostSetup, small = true)
                            online -> {
                                AdButton(
                                    "Disconnect",
                                    onClick = { state.openConfirm(ConfirmKind.DISCONNECT) },
                                    kind = AdButtonKind.OUTLINE,
                                    small = true
                                )
                                Spacer(Modifier.width(8.dp))
                                AdButton("Host settings", onClick = state::openHostSetup, kind = AdButtonKind.OUTLINE, small = true)
                            }
                            else -> {
                                AdButton("Reconnect host", onClick = state::reconnectHost, small = true)
                                Spacer(Modifier.width(8.dp))
                                AdButton("Host settings", onClick = state::openHostSetup, kind = AdButtonKind.OUTLINE, small = true)
                            }
                        }
                    }
                    if (live) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (state.hostConfig.encrypted) "Token encrypted on this device · HTTPS"
                            else "Token encrypted on this device · unencrypted LAN",
                            style = AdType.Tiny
                        )
                    }
                }
            }

            item { SettingsLabel("Cloud models") }
            item {
                val cloud = state.cloudConfig
                AdCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VioletSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(19.dp), tint = VioletInk)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Cloud API", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text(
                                if (cloud.configured) {
                                    "${cloud.model} · ${com.agentdeck.app.data.CloudClient.providerLabel(cloud.baseUrl)}"
                                } else {
                                    "OpenAI-compatible · bring your own key"
                                },
                                style = AdType.Tiny,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        AdTag(
                            if (cloud.configured) "Configured" else "Not set",
                            if (cloud.configured) VioletSoft else GraySoft,
                            if (cloud.configured) VioletInk else GrayInk
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "Replies come directly from the endpoint you configure. Useful without a host for code review, diagnosis, planning, and patch proposals.",
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        color = Muted
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(11.dp), tint = Muted2)
                        Spacer(Modifier.width(4.dp))
                        Text("Analysis only · nothing is written or run", style = AdType.Tiny)
                    }
                    Spacer(Modifier.height(11.dp))
                    AdButton(
                        if (cloud.configured) "Manage cloud API" else "Set up cloud API",
                        onClick = state::openCloudSetup,
                        kind = if (cloud.configured) AdButtonKind.OUTLINE else AdButtonKind.PRIMARY,
                        small = true
                    )
                }
            }

            item { SettingsLabel("Repository context") }
            item {
                val gh = state.githubConfig
                AdCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(GreenSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(19.dp), tint = GreenInk)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("GitHub read-only", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text(
                                if (gh.configured) "${gh.repo}${gh.branch.ifBlank { " · default branch" }}" else "Attach a repository to cloud analysis",
                                style = AdType.Tiny,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        AdTag(
                            if (gh.configured) "Configured" else "Not set",
                            if (gh.configured) GreenSoft else GraySoft,
                            if (gh.configured) GreenInk else GrayInk
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "Fetches README, manifests, and the most relevant source files for the task. Public repos work without a token; private repos need a read-only fine-grained token.",
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        color = Muted
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(11.dp), tint = Muted2)
                        Spacer(Modifier.width(4.dp))
                        Text("Read-only · Contents + Metadata", style = AdType.Tiny)
                    }
                    Spacer(Modifier.height(11.dp))
                    AdButton(
                        if (gh.configured) "Manage GitHub" else "Connect GitHub",
                        onClick = state::openGitHubSetup,
                        kind = AdButtonKind.OUTLINE,
                        small = true
                    )
                }
            }

            item { SettingsLabel("On-device models") }
            item {
                AdCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BlueSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, modifier = Modifier.size(19.dp), tint = BlueInk)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Local inference", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text("Runs on this phone · UI preview", style = AdType.Tiny)
                        }
                        Text(
                            "SIMULATED",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AmberInk,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AmberSoft)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    locals.forEachIndexed { index, model ->
                        if (index > 0) AdDivider()
                        LocalModelRow(model = model, state = state)
                    }
                    AdDivider()
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Storage, contentDescription = null, modifier = Modifier.size(12.dp), tint = Muted2)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "${String.format(Locale.US, "%.1f", importedGB)} GB on device · ${locals.size} models known",
                            style = AdType.Tiny
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "UI prototype: the inference engine is not connected yet. The native build will load GGUF models on this phone. File changes and checks still use the execution host.",
                        fontSize = 9.5.sp,
                        lineHeight = 14.sp,
                        color = Muted2
                    )
                }
            }

            item { SettingsLabel("Defaults for new tasks") }
            item {
                AdCard(padding = 4.dp) {
                    SettingsSelectRow(
                        label = "Project",
                        value = project?.name ?: "Select",
                        options = state.data.projects.map { it.id to it.name },
                        selectedId = settings.defaultProjectId,
                        onSelect = state::setDefaultProject
                    )
                    AdDivider()
                    SettingsSelectRow(
                        label = "Default model",
                        value = defaultModel?.let { model ->
                            when {
                                model.isLocal -> "${model.name} · this phone"
                                model.isCloud -> "${model.name} · cloud"
                                else -> "${model.name} · ${model.providerLabel}"
                            }
                        } ?: "Select",
                        options = state.data.models.map { model ->
                            val label = when {
                                !model.isReady -> "${model.name} · ${state.modelStateLabel(model)}"
                                model.isLocal -> "${model.name} · this phone"
                                model.isCloud -> "${model.name} · cloud"
                                else -> "${model.name} · ${model.providerLabel}"
                            }
                            model.id to label
                        },
                        enabledIds = state.data.models.filter { it.isReady }.map { it.id }.toSet(),
                        selectedId = settings.defaultModelId,
                        onSelect = state::setDefaultModel
                    )
                }
            }

            item { SettingsLabel("Standard permissions") }
            item {
                AdCard(padding = 4.dp) {
                    PermissionRow(Icons.Outlined.Check, GreenSoft, GreenInk, "Read and edit project files", "Limited to the selected repository.")
                    PermissionRow(Icons.Outlined.Check, GreenSoft, GreenInk, "Run tests and linters", "Checks run inside the project sandbox.")
                    PermissionRow(Icons.Outlined.Shield, AmberSoft, AmberInk, "Install packages or use the network", "Requires your approval for each task.")
                    PermissionRow(Icons.Outlined.Cloud, VioletSoft, VioletInk, "Cloud analysis is read-only", "Selected code goes to your API. Nothing is changed or run.")
                    PermissionRow(Icons.Outlined.Code, GreenSoft, GreenInk, "GitHub access is read-only", "Contents and metadata only. No writes, ever.")
                    PermissionRow(Icons.Outlined.Close, RedSoft, RedInk, "Merge, deploy, or change settings", "Never allowed from a task.", isLast = true)
                }
            }

            item { SettingsLabel("Security") }
            item {
                AdCard(padding = 4.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.enableAppLock(!state.appLockEnabled) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(29.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(AmberSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = AmberInk
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("App lock", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(
                                "Require your device screen lock to open AgentDeck.",
                                fontSize = 10.sp,
                                color = Muted,
                                lineHeight = 14.sp
                            )
                        }
                        Switch(
                            checked = state.appLockEnabled,
                            onCheckedChange = state::enableAppLock
                        )
                    }
                    if (state.appLockEnabled) {
                        AdDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { state.lockNow() }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(15.dp), tint = AmberInk)
                            Spacer(Modifier.width(7.dp))
                            Text("Lock now", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AmberInk)
                        }
                    }
                }
            }

            item { SettingsLabel("Backup & restore") }
            item {
                val scope = rememberCoroutineScope()
                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        val json = runCatching { state.exportBackupJson() }.getOrNull()
                        if (json == null) {
                            state.notifyUser("Could not build the backup")
                            return@launch
                        }
                        val saved = runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { output ->
                                output.write(json.toByteArray(Charsets.UTF_8))
                            } ?: error("No output stream")
                        }.isSuccess
                        state.notifyUser(if (saved) "Backup saved" else "Could not save the backup")
                    }
                }
                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        val text = runCatching {
                            context.contentResolver.openInputStream(uri)
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.readText()
                        }.getOrNull()
                        if (text == null) {
                            state.notifyUser("Could not read that file")
                            return@launch
                        }
                        state.notifyUser(state.importBackup(text) ?: "Backup imported")
                    }
                }
                AdCard(padding = 4.dp) {
                    ActionRow(
                        icon = Icons.Outlined.Download,
                        title = "Export workspace",
                        subtitle = "Tasks, messages, diffs, and settings as JSON. API keys and tokens stay on this device.",
                        onClick = { exportLauncher.launch("agentdeck-backup.json") }
                    )
                    AdDivider()
                    ActionRow(
                        icon = Icons.Outlined.Upload,
                        title = "Import workspace",
                        subtitle = "Replaces all tasks on this device with the selected backup.",
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                    )
                }
            }

            item { SettingsLabel("Local data") }
            item {
                AdCard(padding = 4.dp) {
                    SimpleSettingRow("Tasks", "Stored on this device")
                    AdDivider()
                    SimpleSettingRow("Cloud API key", if (state.cloudConfig.keySaved) "Encrypted · Keystore" else "Not saved")
                    AdDivider()
                    SimpleSettingRow("GitHub token", if (state.githubConfig.tokenSaved) "Encrypted · Keystore" else "No token")
                    AdDivider()
                    SimpleSettingRow("Workspace", "Simulated · no code runs")
                    AdDivider()
                    SimpleSettingRow("Local inference", "Preview · not connected")
                    AdDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { state.openConfirm(ConfirmKind.RESET) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(15.dp), tint = RedInk)
                        Spacer(Modifier.width(7.dp))
                        Text("Reset workspace", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RedInk)
                    }
                }
            }

            item { SettingsLabel("Profile") }
            item {
                AdCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(33.dp)
                                .clip(CircleShape)
                                .background(Ink),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("R", color = AdSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Local profile", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text("No account or sign-in · data stays on this device", style = AdType.Tiny)
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("AgentDeck", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "AgentDeck $versionName. Cloud analysis is real when configured. Execution, diffs, checks, and local inference are simulated.",
                        fontSize = 9.5.sp,
                        lineHeight = 14.sp,
                        color = Muted2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text.uppercase(),
        style = AdType.Section,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 1.dp)
    )
}

@Composable
private fun LocalModelRow(model: ModelOption, state: AgentDeckState) {
    val localState = model.localState ?: LocalModelState.NOT_IMPORTED
    val tone = modelStateTone(localState)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Memory, contentDescription = null, modifier = Modifier.size(15.dp), tint = Muted)
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.name, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(6.dp))
                AdTag(localState.label, tone.bg, tone.fg)
            }
            Text(
                "${model.quantization} · ${model.sizeGB} GB · ${model.contextK}K context · ${model.minRamGB} GB RAM min",
                fontSize = 9.sp,
                color = Muted2,
                lineHeight = 13.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        when (localState) {
            LocalModelState.READY -> AdButton(
                "Unload",
                onClick = { state.unloadLocalModel(model.id) },
                kind = AdButtonKind.OUTLINE,
                small = true
            )
            LocalModelState.UNLOADED -> AdButton(
                "Load",
                onClick = { state.loadLocalModel(model.id) },
                small = true
            )
            LocalModelState.NOT_IMPORTED -> AdButton(
                "Import",
                onClick = { state.importLocalModel(model.id) },
                icon = Icons.Outlined.Download,
                small = true
            )
            LocalModelState.LOADING, LocalModelState.IMPORTING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = BlueInk
                )
                Spacer(Modifier.width(5.dp))
                Text(localState.label, fontSize = 10.5.sp, color = BlueInk)
            }
        }
    }
}

private data class SelectOption(val id: String, val label: String)

@Composable
private fun SettingsSelectRow(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    enabledIds: Set<String> = options.map { it.first }.toSet()
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2)
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(13.dp), tint = Muted)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, optionLabel) ->
                    val enabled = id in enabledIds
                    DropdownMenuItem(
                        text = {
                            Text(
                                optionLabel,
                                fontSize = 12.sp,
                                color = if (enabled) Ink else Muted2
                            )
                        },
                        enabled = enabled,
                        onClick = {
                            expanded = false
                            onSelect(id)
                        },
                        trailingIcon = {
                            if (id == selectedId) {
                                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(15.dp), tint = BlueInk)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bg: Color,
    fg: Color,
    title: String,
    subtitle: String,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = fg)
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(subtitle, fontSize = 10.sp, color = Muted, lineHeight = 14.sp)
        }
    }
    if (!isLast) AdDivider(Modifier.padding(start = 45.dp))
}

@Composable
private fun SimpleSettingRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
        Text(value, fontSize = 11.5.sp, color = Muted)
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(29.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = Ink)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(subtitle, fontSize = 10.sp, color = Muted, lineHeight = 14.sp)
        }
    }
}
