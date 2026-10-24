package com.agentdeck.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentdeck.app.data.CheckRun
import com.agentdeck.app.data.CheckStatus
import com.agentdeck.app.data.ChangeType
import com.agentdeck.app.data.LocalModelState
import com.agentdeck.app.data.RootTab
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
import com.agentdeck.app.ui.theme.LineStrong
import com.agentdeck.app.ui.theme.Mono
import com.agentdeck.app.ui.theme.Muted
import com.agentdeck.app.ui.theme.Muted2
import com.agentdeck.app.ui.theme.RedInk
import com.agentdeck.app.ui.theme.RedSoft
import com.agentdeck.app.ui.theme.Surface2
import com.agentdeck.app.ui.theme.Surface3
import com.agentdeck.app.ui.theme.Surface as AdSurface
import com.agentdeck.app.ui.theme.VioletInk
import com.agentdeck.app.ui.theme.VioletSoft
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val CardShape = RoundedCornerShape(16.dp)
val SmallShape = RoundedCornerShape(12.dp)
val PillShape = RoundedCornerShape(999.dp)

object AdType {
    val Tiny = TextStyle(fontSize = 9.5.sp, lineHeight = 13.sp, color = Muted2)
    val Meta = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, color = Muted)
    val Section = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.7.sp, color = Muted)
    val CardTitle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, color = Ink)
    val CardBody = TextStyle(fontSize = 11.5.sp, lineHeight = 17.sp, color = Muted)
    val Code = TextStyle(fontFamily = Mono, fontSize = 11.sp, lineHeight = 17.sp, color = InkSoft)
    val Micro = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
}

data class AdTone(val bg: Color, val fg: Color)

fun statusTone(status: TaskStatus): AdTone = when (status) {
    TaskStatus.PENDING -> AdTone(GraySoft, GrayInk)
    TaskStatus.IN_PROGRESS -> AdTone(BlueSoft, BlueInk)
    TaskStatus.NEEDS_INPUT -> AdTone(VioletSoft, VioletInk)
    TaskStatus.REVIEW -> AdTone(AmberSoft, AmberInk)
    TaskStatus.FAILED -> AdTone(RedSoft, RedInk)
    TaskStatus.COMPLETED -> AdTone(GreenSoft, GreenInk)
}

fun statusIcon(status: TaskStatus): ImageVector = when (status) {
    TaskStatus.PENDING -> Icons.Outlined.Schedule
    TaskStatus.IN_PROGRESS -> Icons.Outlined.Sync
    TaskStatus.NEEDS_INPUT -> Icons.Outlined.ChatBubbleOutline
    TaskStatus.REVIEW -> Icons.Outlined.Visibility
    TaskStatus.FAILED -> Icons.Outlined.ErrorOutline
    TaskStatus.COMPLETED -> Icons.Outlined.CheckCircle
}

fun checkTone(status: CheckStatus): AdTone = when (status) {
    CheckStatus.PASSED -> AdTone(GreenSoft, GreenInk)
    CheckStatus.FAILED -> AdTone(RedSoft, RedInk)
    CheckStatus.SKIPPED, CheckStatus.NOT_RUN -> AdTone(GraySoft, GrayInk)
}

fun checkIcon(status: CheckStatus): ImageVector = when (status) {
    CheckStatus.PASSED -> Icons.Outlined.CheckCircle
    CheckStatus.FAILED -> Icons.Outlined.ErrorOutline
    CheckStatus.SKIPPED -> Icons.Outlined.RemoveCircleOutline
    CheckStatus.NOT_RUN -> Icons.Outlined.RadioButtonUnchecked
}

fun modelStateTone(state: LocalModelState?): AdTone = when (state) {
    LocalModelState.READY -> AdTone(GreenSoft, GreenInk)
    LocalModelState.LOADING, LocalModelState.IMPORTING -> AdTone(BlueSoft, BlueInk)
    else -> AdTone(GraySoft, GrayInk)
}

fun changeTone(type: ChangeType): AdTone = when (type) {
    ChangeType.ADDED -> AdTone(GreenSoft, GreenInk)
    ChangeType.DELETED -> AdTone(RedSoft, RedInk)
    ChangeType.RENAMED -> AdTone(AmberSoft, AmberInk)
    ChangeType.MODIFIED -> AdTone(BlueSoft, BlueInk)
}

fun changeLetter(type: ChangeType): String = when (type) {
    ChangeType.ADDED -> "A"
    ChangeType.DELETED -> "D"
    ChangeType.RENAMED -> "R"
    ChangeType.MODIFIED -> "M"
}

data class CheckSummary(val label: String, val tone: AdTone, val failed: Boolean)

fun summarizeChecks(checks: List<CheckRun>): CheckSummary {
    val failed = checks.count { it.status == CheckStatus.FAILED }
    val passed = checks.count { it.status == CheckStatus.PASSED }
    val notRun = checks.count { it.status == CheckStatus.NOT_RUN }
    val skipped = checks.count { it.status == CheckStatus.SKIPPED }
    return when {
        failed > 0 -> CheckSummary("$failed check${if (failed == 1) "" else "s"} failed", AdTone(RedSoft, RedInk), true)
        passed > 0 -> {
            val suffix = buildString {
                if (notRun > 0) append(" · $notRun not run")
                if (skipped > 0) append(" · $skipped skipped")
            }
            CheckSummary("$passed passed$suffix", AdTone(GreenSoft, GreenInk), false)
        }
        checks.isEmpty() -> CheckSummary("No checks run", AdTone(GraySoft, GrayInk), false)
        else -> CheckSummary("Checks not run", AdTone(GraySoft, GrayInk), false)
    }
}

fun relativeTime(timestamp: Long): String {
    val seconds = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0)) / 1000
    return when {
        seconds < 45 -> "just now"
        seconds < 90 -> "1m ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3600}h ago"
        seconds < 604_800 -> "${seconds / 86_400}d ago"
        else -> DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
            .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
    }
}

fun clockTime(timestamp: Long): String =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

fun fullDate(timestamp: Long): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

@Composable
fun AdCard(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = AdSurface,
        border = BorderStroke(1.dp, Line),
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun AdDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = Line, thickness = 1.dp)
}

@Composable
fun AdIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier.size(17.dp),
    tint: Color = Muted,
    description: String? = null
) {
    Icon(icon, contentDescription = description, modifier = modifier, tint = tint)
}

@Composable
fun AdIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 38.dp
) {
    val bg = if (filled) Ink else AdSurface
    val fg = if (filled) AdSurface else InkSoft
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .border(1.dp, if (filled) Ink else Line, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = fg, modifier = Modifier.size(18.dp))
    }
}

enum class AdButtonKind { PRIMARY, OUTLINE, DANGER }

@Composable
fun AdButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: AdButtonKind = AdButtonKind.PRIMARY,
    enabled: Boolean = true,
    small: Boolean = false,
    fillWidth: Boolean = false
) {
    val height = if (small) 38.dp else 44.dp
    val shape = RoundedCornerShape(if (small) 10.dp else 12.dp)
    val contentPadding = PaddingValues(horizontal = if (small) 12.dp else 16.dp)
    val buttonModifier = (if (fillWidth) modifier.fillMaxWidth() else modifier).height(height)

    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (small) 14.dp else 16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = if (small) 11.5.sp else 13.sp, fontWeight = FontWeight.SemiBold)
    }

    when (kind) {
        AdButtonKind.PRIMARY -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = AdSurface)
        ) { content() }
        AdButtonKind.DANGER -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(containerColor = RedInk, contentColor = AdSurface)
        ) { content() }
        AdButtonKind.OUTLINE -> OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            border = BorderStroke(1.dp, LineStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
        ) { content() }
    }
}

@Composable
fun AdTag(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(9.dp), tint = fg)
            Spacer(Modifier.width(3.dp))
        }
        Text(text, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = fg, lineHeight = 11.sp)
    }
}

@Composable
fun LocalChip(label: String = "Local") {
    AdTag(label, BlueSoft, BlueInk, icon = Icons.Outlined.PhoneAndroid)
}

@Composable
fun CloudChip(label: String = "Cloud") {
    AdTag(label, VioletSoft, VioletInk, icon = Icons.Outlined.Cloud)
}

@Composable
fun AnalysisTag() {
    Text(
        "ANALYSIS ONLY",
        fontSize = 7.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.4.sp,
        color = VioletInk,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(VioletSoft)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

@Composable
fun PreviewTag(text: String = "Simulated") {
    Text(
        text.uppercase(),
        fontSize = 7.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.4.sp,
        color = Muted,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(GraySoft)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

@Composable
fun DemoTag() {
    Text(
        "LOCAL",
        fontSize = 7.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.5.sp,
        color = Muted,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Surface3)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}

@Composable
fun StatusPill(status: TaskStatus) {
    val tone = statusTone(status)
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(tone.bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(statusIcon(status), contentDescription = null, modifier = Modifier.size(11.dp), tint = tone.fg)
        Spacer(Modifier.width(4.dp))
        Text(status.label, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = tone.fg)
    }
}

@Composable
fun CheckPill(status: CheckStatus) {
    val tone = checkTone(status)
    Text(
        status.label,
        fontSize = 8.5.sp,
        fontWeight = FontWeight.Bold,
        color = tone.fg,
        modifier = Modifier
            .clip(PillShape)
            .background(tone.bg)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
fun RootTopBar(
    title: String,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {}
) {
    Column(Modifier.fillMaxWidth().background(AdSurface).statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(subtitle, style = AdType.Tiny, modifier = Modifier.padding(top = 1.dp))
                }
            }
            actions()
        }
    }
}

@Composable
fun HomeTopBar(
    onDemo: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AdSurface)
            .statusBarsPadding()
            .heightIn(min = 66.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("AgentDeck", style = MaterialTheme.typography.headlineLarge, color = Ink)
            Text("Coding workspace", style = AdType.Tiny)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .clip(PillShape)
                    .border(1.dp, Line, PillShape)
                    .clickable(onClick = onDemo)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Info, contentDescription = "About this workspace", modifier = Modifier.size(12.dp), tint = Muted)
                Spacer(Modifier.width(4.dp))
                Text("LOCAL", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = 0.5.sp)
            }
            Box(
                modifier = Modifier
                    .size(33.dp)
                    .clip(CircleShape)
                    .background(Ink)
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center
            ) {
                Text("R", color = AdSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AdBackTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AdSurface)
            .statusBarsPadding()
            .heightIn(min = 58.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AdIconButton(Icons.Outlined.ArrowBack, "Back", onBack, size = 36.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = AdType.Tiny, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun AdSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(SmallShape)
            .background(AdSurface)
            .border(1.dp, Line, SmallShape)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(17.dp), tint = Muted2)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 13.sp, color = Muted2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = Ink),
                cursorBrush = SolidColor(BlueInk)
            )
        }
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Surface3)
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear search", modifier = Modifier.size(12.dp), tint = Muted)
            }
        }
    }
}

@Composable
fun AdTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 46.dp,
    mono: Boolean = false,
    singleLine: Boolean = false,
    secret: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(SmallShape)
            .background(AdSurface)
            .border(1.dp, Line, SmallShape)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, fontSize = 13.sp, color = Muted2, lineHeight = 19.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            textStyle = TextStyle(
                fontSize = if (mono) 11.5.sp else 13.sp,
                lineHeight = 19.sp,
                color = Ink,
                fontFamily = if (mono) Mono else FontFamily.SansSerif
            ),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (secret) {
                KeyboardOptions(keyboardType = KeyboardType.Password)
            } else {
                KeyboardOptions.Default
            },
            cursorBrush = SolidColor(BlueInk)
        )
    }
}

@Composable
fun AdChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int? = null
) {
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(if (selected) Ink else AdSurface)
            .border(1.dp, if (selected) Ink else Line, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) AdSurface else Muted
        )
        if (count != null) {
            Spacer(Modifier.width(5.dp))
            Text(
                count.toString(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) AdSurface.copy(alpha = 0.8f) else Muted,
                modifier = Modifier
                    .clip(PillShape)
                    .background(if (selected) AdSurface.copy(alpha = 0.18f) else Surface3)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
        if (hint != null) {
            Spacer(Modifier.weight(1f))
            Text(hint, style = AdType.Tiny)
        } else if (action == null) {
            Spacer(Modifier.weight(1f))
        }
        if (action != null) {
            Spacer(Modifier.weight(1f))
            action()
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = AdType.Section,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 7.dp)
    )
}

@Composable
fun AdEmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = AdSurface,
        border = BorderStroke(1.dp, Line)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Surface2)
                    .border(1.dp, Line, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = Muted)
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(5.dp))
            Text(text, style = AdType.Meta, textAlign = TextAlign.Center)
            if (action != null) {
                Spacer(Modifier.height(14.dp))
                action()
            }
        }
    }
}

@Composable
fun AdTaskCard(
    task: Task,
    projectName: String,
    modelName: String,
    isLocal: Boolean,
    isCloud: Boolean = false,
    onClick: () -> Unit
) {
    val summary = summarizeChecks(task.checks)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AdSurface,
        border = BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    task.title,
                    style = AdType.CardTitle,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(task.status)
            }
            Spacer(Modifier.height(5.dp))
            FlowRowMeta {
                Text(projectName, style = AdType.Meta, maxLines = 1)
                Text("·", style = AdType.Meta, color = Muted2)
                Text(modelName, style = AdType.Meta, maxLines = 1)
                if (isLocal) LocalChip()
                if (isCloud) CloudChip()
                Text("·", style = AdType.Meta, color = Muted2)
                Text(relativeTime(task.updatedAt), style = AdType.Meta)
            }
            if (task.changedFiles.isNotEmpty() || task.checks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AdDivider()
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (task.changedFiles.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(12.dp), tint = Muted)
                            Spacer(Modifier.width(4.dp))
                            Text("${task.changedFiles.size} file${if (task.changedFiles.size == 1) "" else "s"}", style = AdType.Tiny)
                        }
                    }
                    if (task.checks.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (summary.failed) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = summary.tone.fg
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(summary.label, style = AdType.Tiny, color = summary.tone.fg)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowMeta(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

@Composable
fun AdStatTile(
    label: String,
    count: Int,
    icon: ImageVector,
    tone: AdTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = AdSurface,
        border = BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(25.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tone.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = tone.fg)
                }
                Spacer(Modifier.weight(1f))
                Text(count.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
            }
            Spacer(Modifier.height(5.dp))
            Text(label, fontSize = 9.5.sp, lineHeight = 11.sp, fontWeight = FontWeight.Medium, color = Muted)
        }
    }
}

@Composable
fun AdBottomBar(active: RootTab, onSelect: (RootTab) -> Unit) {
    val tabs = listOf(
        Triple(RootTab.HOME, "Home", Icons.Outlined.Home),
        Triple(RootTab.TASKS, "Tasks", Icons.Outlined.Description),
        Triple(RootTab.PROJECTS, "Projects", Icons.Outlined.GridView),
        Triple(RootTab.SETTINGS, "Settings", Icons.Outlined.Settings)
    )
    Column(Modifier.fillMaxWidth().background(AdSurface)) {
        AdDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            tabs.forEach { (tab, label, icon) ->
                val selected = tab == active
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(selected = selected, onClick = { onSelect(tab) }, role = Role.Tab)
                        .padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(27.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) BlueSoft else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = label, modifier = Modifier.size(19.dp), tint = if (selected) BlueInk else Muted2)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        label,
                        fontSize = 9.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) BlueInk else Muted2
                    )
                }
            }
        }
    }
}

@Composable
fun InfoBanner(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    kind: BannerKind = BannerKind.WARNING,
    action: (@Composable () -> Unit)? = null
) {
    val bg: Color
    val fg: Color
    val border: Color
    val icon: ImageVector
    when (kind) {
        BannerKind.WARNING -> {
            bg = androidx.compose.ui.graphics.Color(0xFFFFF9EB)
            fg = AmberInk
            border = androidx.compose.ui.graphics.Color(0xFFEEDaAB)
            icon = Icons.Outlined.Warning
        }
        BannerKind.LOCAL -> {
            bg = BlueSoft
            fg = BlueInk
            border = androidx.compose.ui.graphics.Color(0xFFC9D8F5)
            icon = Icons.Outlined.PhoneAndroid
        }
        BannerKind.DONE -> {
            bg = GreenSoft
            fg = GreenInk
            border = androidx.compose.ui.graphics.Color(0xFFCDEBDD)
            icon = Icons.Outlined.CheckCircle
        }
        BannerKind.CLOUD -> {
            bg = VioletSoft
            fg = VioletInk
            border = androidx.compose.ui.graphics.Color(0xFFDCD5F7)
            icon = Icons.Outlined.Cloud
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = fg)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = fg)
                Spacer(Modifier.height(1.dp))
                Text(text, fontSize = 10.5.sp, lineHeight = 14.sp, color = fg.copy(alpha = 0.82f))
            }
            if (action != null) {
                Spacer(Modifier.width(8.dp))
                action()
            }
        }
    }
}

enum class BannerKind { WARNING, LOCAL, CLOUD, DONE }

@Composable
fun SheetHeader(title: String, subtitle: String? = null, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = AdType.Meta)
            }
        }
        AdIconButton(Icons.Outlined.Close, "Close", onClose)
    }
}

@Composable
fun AdClickRow(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) { content() }
}
