package com.agentdeck.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentdeck.app.data.AgentDeckState
import com.agentdeck.app.data.Project
import com.agentdeck.app.data.TaskStatus
import com.agentdeck.app.ui.theme.Surface as AdSurface
import com.agentdeck.app.ui.theme.AmberInk
import com.agentdeck.app.ui.theme.AmberSoft
import com.agentdeck.app.ui.theme.BlueInk
import com.agentdeck.app.ui.theme.BlueSoft
import com.agentdeck.app.ui.theme.GreenInk
import com.agentdeck.app.ui.theme.GreenSoft
import com.agentdeck.app.ui.theme.Ink
import com.agentdeck.app.ui.theme.Line
import com.agentdeck.app.ui.theme.Muted
import com.agentdeck.app.ui.theme.Muted2
import com.agentdeck.app.ui.theme.Surface2

@Composable
fun ProjectsScreen(state: AgentDeckState) {
    val host = state.data.host
    val online = host.connected
    Column(Modifier.fillMaxSize().background(Surface2)) {
        RootTopBar(
            title = "Projects",
            subtitle = "Switch the workspace the app is scoped to.",
            actions = {
                AdIconButton(Icons.Outlined.Add, "New task", state::openNewTask, filled = true)
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.hostConfig.configured && !online) {
                item {
                    InfoBanner(
                        title = "${state.data.host.name} is offline",
                        text = "Cloud and local chat still work. Host models and execution need a reconnect.",
                        action = {
                            AdButton("Reconnect", onClick = state::reconnectHost, kind = AdButtonKind.OUTLINE, small = true)
                        }
                    )
                }
            }
            item {
                HostCard(
                    state = state,
                    online = online
                )
            }
            item {
                Text(
                    "YOUR PROJECTS",
                    style = AdType.Section,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }
            items(state.data.projects, key = { it.id }) { project ->
                ProjectRow(
                    state = state,
                    project = project,
                    active = project.id == state.data.settings.activeProjectId,
                    onClick = { state.openProjectTasks(project.id) }
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BlueSoft)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp), tint = BlueInk)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Local models run on this phone. Projects still use the execution host for file changes and checks.",
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = BlueInk
                    )
                }
            }
        }
    }
}

@Composable
private fun HostCard(state: AgentDeckState, online: Boolean) {
    val host = state.data.host
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
                Text(host.name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("${host.platform} · ${host.transport}", style = AdType.Tiny)
            }
            Row(
                modifier = Modifier
                    .clip(PillShape)
                    .background(if (online) GreenSoft else AmberSoft)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (online) GreenInk else AmberInk))
                Spacer(Modifier.width(5.dp))
                Text(
                    if (online) "Connected" else "Offline",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (online) GreenInk else AmberInk
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (online) {
                "Last seen ${relativeTime(host.lastSeen)}. Host models and all task execution use this simulated host."
            } else {
                "Host tasks are stopped and can be retried. On-device chat remains available."
            },
            style = AdType.Meta
        )
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (online) {
                AdButton("Manage connection", onClick = state::openSettings, kind = AdButtonKind.OUTLINE, small = true)
            } else {
                AdButton("Reconnect host", onClick = state::reconnectHost, small = true)
            }
            Spacer(Modifier.width(10.dp))
            Text("Host state is simulated", style = AdType.Tiny)
        }
    }
}

@Composable
private fun ProjectRow(
    state: AgentDeckState,
    project: Project,
    active: Boolean,
    onClick: () -> Unit
) {
    val tasks = state.tasksFor(project.id)
    val counts = state.countsFor(tasks)
    val open = tasks.count { it.status != TaskStatus.COMPLETED }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AdSurface,
        border = BorderStroke(1.dp, if (active) androidx.compose.ui.graphics.Color(0xFFC5D5F6) else Line)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) BlueSoft else Surface2),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(19.dp), tint = if (active) BlueInk else Ink)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(project.name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ink)
                    if (active) {
                        Spacer(Modifier.width(7.dp))
                        Row(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(BlueSoft)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(10.dp), tint = BlueInk)
                            Spacer(Modifier.width(2.dp))
                            Text("Active", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = BlueInk)
                        }
                    }
                }
                Text(
                    "${project.repo} · ${project.defaultBranch}",
                    style = AdType.Tiny,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$open open task${if (open == 1) "" else "s"} · ${counts[TaskStatus.REVIEW] ?: 0} in review · ${counts[TaskStatus.COMPLETED] ?: 0} completed",
                    fontSize = 10.sp,
                    color = Muted2,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = Muted2)
        }
    }
}
