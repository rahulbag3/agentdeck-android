package com.agentdeck.app.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.agentdeck.app.data.StatusFilter
import com.agentdeck.app.data.Task
import com.agentdeck.app.data.TaskStatus
import com.agentdeck.app.data.TaskKind
import com.agentdeck.app.data.matchesFilter
import com.agentdeck.app.ui.theme.Surface as AdSurface
import com.agentdeck.app.ui.theme.BlueInk
import com.agentdeck.app.ui.theme.Ink
import com.agentdeck.app.ui.theme.Muted
import com.agentdeck.app.ui.theme.Muted2
import com.agentdeck.app.ui.theme.Surface2

@Composable
fun TasksScreen(state: AgentDeckState, kind: TaskKind = TaskKind.CODING) {
    val project = state.activeProject
    val all = state.tasksOfKind(kind)
    val filtered = state.filteredTasksOfKind(kind, state.taskQuery, state.statusFilter)
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val taskCard: @Composable (Task) -> Unit = { task ->
        AdTaskCard(
            task = task,
            projectName = state.project(task.projectId)?.name ?: project.name,
            modelName = state.taskModelName(task),
            isLocal = state.isLocalTask(task),
            isCloud = state.isCloudTask(task),
            onClick = { state.openTask(task.id) },
            onDelete = { state.deleteTask(task.id) }
        )
    }

    Column(Modifier.fillMaxSize().background(Surface2)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AdSurface)
                .statusBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 9.dp, bottom = 9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (kind == TaskKind.CHAT) "Chats" else "Coding",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { state.openProjectSheet() }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(13.dp), tint = Muted)
                        Spacer(Modifier.width(4.dp))
                        Text(project.name, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Muted)
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(13.dp), tint = Muted2)
                    }
                }
                AdIconButton(Icons.Outlined.Add, "New task", { state.startQuickSession(kind) }, filled = true)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                AdSearchField(
                    value = state.taskQuery,
                    onValueChange = { state.taskQuery = it },
                    placeholder = "Search tasks"
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(StatusFilter.values().toList()) { filter ->
                        AdChip(
                            text = filter.label,
                            selected = state.statusFilter == filter,
                            count = all.count { it.status.matchesFilter(filter) },
                            onClick = { state.statusFilter = filter }
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${filtered.size} task${if (filtered.size == 1) "" else "s"}",
                        style = AdType.Tiny,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Switch project",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { state.openProjectSheet() }
                            .padding(horizontal = 3.dp, vertical = 3.dp)
                    )
                }
            }
            if (filtered.isEmpty()) {
                item {
                    AdEmptyState(
                        icon = Icons.Outlined.Search,
                        title = "No tasks match",
                        text = "Adjust the search or status filter.",
                        action = {
                            AdButton(
                                "Clear filters",
                                onClick = { state.clearFilters() },
                                kind = AdButtonKind.OUTLINE,
                                small = true
                            )
                        }
                    )
                }
            } else if (landscape) {
                items(filtered.chunked(2)) { row ->
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
                items(filtered, key = { it.id }) { task ->
                    taskCard(task)
                }
            }
        }
    }
}
