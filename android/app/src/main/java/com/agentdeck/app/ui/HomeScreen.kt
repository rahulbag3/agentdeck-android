package com.agentdeck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
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
import com.agentdeck.app.data.TaskKind
import com.agentdeck.app.data.AgentDeckState
import com.agentdeck.app.data.StatusFilter
import com.agentdeck.app.data.Task
import com.agentdeck.app.data.TaskStatus
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
import com.agentdeck.app.ui.theme.Muted
import com.agentdeck.app.ui.theme.Muted2
import com.agentdeck.app.ui.theme.RedInk
import com.agentdeck.app.ui.theme.RedSoft
import com.agentdeck.app.ui.theme.Surface2
import com.agentdeck.app.ui.theme.VioletInk
import com.agentdeck.app.ui.theme.VioletSoft
import com.agentdeck.app.ui.theme.Surface as AdSurface

@Composable
fun HomeScreen(state: AgentDeckState) {
    val recent = state.data.tasks.sortedByDescending { it.updatedAt }.take(5)
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val taskCard: @Composable (Task) -> Unit = { task ->
        AdTaskCard(
            task = task,
            projectName = state.project(task.projectId)?.name ?: "",
            modelName = state.taskModelName(task),
            isLocal = state.isLocalTask(task),
            isCloud = state.isCloudTask(task),
            onClick = { state.openTask(task.id) },
            onDelete = { state.deleteTask(task.id) }
        )
    }

    Column(Modifier.fillMaxSize().background(Surface2)) {
        HomeTopBar(onAbout = state::openAbout)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ModeLauncherCard(
                        title = "Chat",
                        subtitle = "AI assistant — ask anything, research the web, use memory, call APIs",
                        onClick = { state.startQuickSession(TaskKind.CHAT) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeLauncherCard(
                        title = "Code",
                        subtitle = "Coding agent — edit files, run commands, manage git, review diffs",
                        onClick = { state.startQuickSession(TaskKind.CODING) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Recent",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        recent.size.toString(),
                        fontFamily = com.agentdeck.app.ui.theme.Mono,
                        fontSize = 9.5.sp,
                        color = Muted2
                    )
                }
            }

            if (recent.isEmpty()) {
                item {
                    AdEmptyState(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = "Nothing here yet",
                        text = "Pick a mode above and start — everything runs on this phone.",
                        action = null
                    )
                }
            } else if (landscape) {
                items(recent.chunked(2)) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { task ->
                            Box(Modifier.weight(1f)) { taskCard(task) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                items(recent, key = { it.id }) { task ->
                    taskCard(task)
                }
            }

            item {
                Text(
                    "AI assistant and coding agent · everything runs on this phone",
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp,
                    color = Muted2
                )
            }
        }
    }
}

@Composable
private fun ProjectSwitcherCard(
    projectName: String,
    repo: String,
    branch: String,
    hostConnected: Boolean,
    lastSeen: Long,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = AdSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                Text(projectName, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("$repo · $branch", style = AdType.Tiny, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (hostConnected) GreenInk else AmberInk)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (hostConnected) "Synced ${relativeTime(lastSeen)}" else "Offline",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (hostConnected) Muted else AmberInk
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(15.dp), tint = Muted2)
        }
    }
}

@Composable
private fun StatsGrid(tasks: List<Task>, onFilter: (StatusFilter) -> Unit) {
    val counts = androidx.compose.runtime.remember(tasks) { tasks.groupingBy { it.status }.eachCount() }
    val stats = listOf(
        Triple("Active", counts[TaskStatus.IN_PROGRESS] ?: 0, Icons.Outlined.Sync) to (StatusFilter.ACTIVE to AdTone(BlueSoft, BlueInk)),
        Triple("Needs input", counts[TaskStatus.NEEDS_INPUT] ?: 0, Icons.Outlined.ChatBubbleOutline) to (StatusFilter.NEEDS_INPUT to AdTone(VioletSoft, VioletInk)),
        Triple("Review", counts[TaskStatus.REVIEW] ?: 0, Icons.Outlined.Visibility) to (StatusFilter.REVIEW to AdTone(AmberSoft, AmberInk)),
        Triple("Pending", counts[TaskStatus.PENDING] ?: 0, Icons.Outlined.Schedule) to (StatusFilter.PENDING to AdTone(GraySoft, GrayInk)),
        Triple("Failed", counts[TaskStatus.FAILED] ?: 0, Icons.Outlined.ErrorOutline) to (StatusFilter.FAILED to AdTone(RedSoft, RedInk)),
        Triple("Completed", counts[TaskStatus.COMPLETED] ?: 0, Icons.Outlined.CheckCircle) to (StatusFilter.COMPLETED to AdTone(GreenSoft, GreenInk))
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 360.dp) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (info, filterTone) ->
                        AdStatTile(
                            label = info.first,
                            count = info.second,
                            icon = info.third,
                            tone = filterTone.second,
                            onClick = { onFilter(filterTone.first) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}


@Composable
private fun ModeLauncherCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AdSurface)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(25.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2)
                    .border(1.dp, Line, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (title == "Chat") Icons.Outlined.ChatBubbleOutline else Icons.Outlined.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = InkSoft
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Muted2
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(subtitle, fontSize = 9.5.sp, lineHeight = 13.sp, color = Muted2)
    }
}
