package com.agentdeck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentdeck.app.ui.theme.BlueInk
import com.agentdeck.app.ui.theme.Ink
import com.agentdeck.app.ui.theme.InkSoft
import com.agentdeck.app.ui.theme.Line
import com.agentdeck.app.ui.theme.Mono
import com.agentdeck.app.ui.theme.Muted
import com.agentdeck.app.ui.theme.Surface2
import com.agentdeck.app.ui.theme.Surface3

/**
 * A deliberately small Markdown subset for model replies: paragraphs,
 * headings, flat lists, quotes, rules, fenced code, inline code, bold, italic,
 * and safe http(s) links. Unknown syntax is shown as plain text.
 */
internal sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
    data class NumberedList(val start: Int, val items: List<String>) : MarkdownBlock
    data class CodeFence(val language: String, val code: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data object Rule : MarkdownBlock
}

internal data class InlineSegment(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val link: String? = null
)

private val headingPattern = Regex("""^(#{1,6})\s+(.*)$""")
private val bulletPattern = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val orderedPattern = Regex("""^(\s*)(\d{1,9})[.)]\s+(.*)$""")

internal fun parseMarkdown(source: String): List<MarkdownBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    var index = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            val text = paragraph.joinToString(" ").trim()
            if (text.isNotEmpty()) blocks += MarkdownBlock.Paragraph(text)
            paragraph.clear()
        }
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val language = trimmed.removePrefix("```").trim()
            val code = mutableListOf<String>()
            index += 1
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                code += lines[index]
                index += 1
            }
            if (index < lines.size) index += 1
            blocks += MarkdownBlock.CodeFence(language, code.joinToString("\n"))
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            index += 1
            continue
        }

        val heading = headingPattern.matchEntire(trimmed)
        if (heading != null) {
            flushParagraph()
            blocks += MarkdownBlock.Heading(
                level = heading.groupValues[1].length,
                text = heading.groupValues[2].trim()
            )
            index += 1
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushParagraph()
            blocks += MarkdownBlock.Rule
            index += 1
            continue
        }

        if (trimmed.startsWith(">")) {
            flushParagraph()
            val quote = mutableListOf<String>()
            while (index < lines.size) {
                val quoted = lines[index].trimStart()
                if (!quoted.startsWith(">")) break
                quote += quoted.removePrefix(">").removePrefix(" ").trimEnd()
                index += 1
            }
            blocks += MarkdownBlock.Quote(quote.joinToString("\n").trim())
            continue
        }

        val firstBullet = bulletPattern.matchEntire(line)
        val firstOrdered = orderedPattern.matchEntire(line)
        if (firstBullet != null || firstOrdered != null) {
            flushParagraph()
            val ordered = firstBullet == null
            val start = firstOrdered?.groupValues?.get(2)?.toIntOrNull() ?: 1
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val current = lines[index]
                val bullet = bulletPattern.matchEntire(current)
                val orderedItem = orderedPattern.matchEntire(current)
                val item = if (ordered) orderedItem?.groupValues?.get(3) else bullet?.groupValues?.get(2)
                if (item != null) {
                    items += item.trim()
                    index += 1
                    while (index < lines.size) {
                        val continuation = lines[index]
                        if (continuation.isBlank()) break
                        if (bulletPattern.matchEntire(continuation) != null ||
                            orderedPattern.matchEntire(continuation) != null
                        ) {
                            break
                        }
                        if (continuation.firstOrNull()?.isWhitespace() != true) break
                        items[items.lastIndex] = items.last().trimEnd() + " " + continuation.trim()
                        index += 1
                    }
                } else {
                    break
                }
            }
            blocks += if (ordered) {
                MarkdownBlock.NumberedList(start, items)
            } else {
                MarkdownBlock.BulletList(items)
            }
            continue
        }

        paragraph += trimmed
        index += 1
    }
    flushParagraph()
    return blocks
}

internal fun parseInline(
    source: String,
    bold: Boolean = false,
    italic: Boolean = false,
    link: String? = null
): List<InlineSegment> {
    val result = mutableListOf<InlineSegment>()
    val plain = StringBuilder()
    var index = 0

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            result += InlineSegment(plain.toString(), bold, italic, code = false, link = link)
            plain.clear()
        }
    }

    while (index < source.length) {
        val char = source[index]

        if (char == '\\' && index + 1 < source.length) {
            plain.append(source[index + 1])
            index += 2
            continue
        }

        if (char == '`') {
            val end = source.indexOf('`', index + 1)
            if (end > index + 1) {
                flushPlain()
                result += InlineSegment(
                    text = source.substring(index + 1, end),
                    bold = bold,
                    italic = italic,
                    code = true,
                    link = link
                )
                index = end + 1
                continue
            }
        }

        if ((source.startsWith("**", index) || source.startsWith("__", index))) {
            val marker = source.substring(index, index + 2)
            val end = source.indexOf(marker, index + 2)
            if (end > index + 2) {
                flushPlain()
                result += parseInline(source.substring(index + 2, end), bold = true, italic = italic, link = link)
                index = end + 2
                continue
            }
        }

        if (char == '[') {
            val labelEnd = source.indexOf(']', index + 1)
            if (labelEnd > index + 1 && labelEnd + 1 < source.length && source[labelEnd + 1] == '(') {
                val urlEnd = source.indexOf(')', labelEnd + 2)
                if (urlEnd > labelEnd + 2) {
                    val label = source.substring(index + 1, labelEnd)
                    val url = source.substring(labelEnd + 2, urlEnd).trim()
                    if (label.isNotBlank() && url.isNotBlank() && !url.any { it.isWhitespace() }) {
                        flushPlain()
                        result += parseInline(label, bold, italic, link = url)
                        index = urlEnd + 1
                        continue
                    }
                }
            }
        }

        if (char == '*' || char == '_') {
            val wordBoundary = char != '_' ||
                index == 0 ||
                !source[index - 1].isLetterOrDigit()
            if (wordBoundary) {
                val end = source.indexOf(char, index + 1)
                val closeBoundary = end < 0 || char != '_' ||
                    end + 1 >= source.length ||
                    !source[end + 1].isLetterOrDigit()
                if (end > index + 1 && closeBoundary) {
                    flushPlain()
                    result += parseInline(source.substring(index + 1, end), bold, italic = true, link = link)
                    index = end + 1
                    continue
                }
            }
        }

        plain.append(char)
        index += 1
    }
    flushPlain()
    return result
}

internal fun safeLink(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

@Composable
internal fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> MarkdownInline(block.text)
                is MarkdownBlock.Heading -> {
                    val size = when (block.level) {
                        1 -> 16.sp
                        2 -> 14.5.sp
                        else -> 13.sp
                    }
                    val lineHeight = when (block.level) {
                        1 -> 21.sp
                        2 -> 20.sp
                        else -> 18.sp
                    }
                    MarkdownInline(
                        source = block.text,
                        fontSize = size,
                        lineHeight = lineHeight,
                        fontWeight = FontWeight.Bold
                    )
                }
                is MarkdownBlock.BulletList -> MarkdownList(block.items, start = null)
                is MarkdownBlock.NumberedList -> MarkdownList(block.items, start = block.start)
                is MarkdownBlock.CodeFence -> MarkdownCode(block)
                is MarkdownBlock.Quote -> MarkdownQuote(block.text)
                MarkdownBlock.Rule -> AdDivider(Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun MarkdownList(items: List<String>, start: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                Text(
                    text = if (start == null) "•" else "${start + index}.",
                    modifier = Modifier.widthIn(min = if (start == null) 14.dp else 20.dp),
                    fontFamily = if (start == null) null else Mono,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = Muted
                )
                MarkdownInline(item, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MarkdownQuote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = androidx.compose.ui.Alignment.Top
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(Line)
        )
        Spacer(Modifier.width(8.dp))
        MarkdownInline(
            source = text,
            modifier = Modifier.weight(1f),
            color = InkSoft,
            fontStyle = FontStyle.Italic
        )
    }
}

@Composable
private fun MarkdownCode(block: MarkdownBlock.CodeFence) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(9.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface3)
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Text(
                text = block.language.ifBlank { "code" },
                fontFamily = Mono,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = Muted
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            Text(
                text = block.code,
                fontFamily = Mono,
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                color = Ink,
                softWrap = false
            )
        }
    }
}

@Composable
private fun MarkdownInline(
    source: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.5.sp,
    lineHeight: TextUnit = 19.sp,
    color: Color = Ink,
    fontWeight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle = FontStyle.Normal
) {
    val segments = remember(source) { parseInline(source) }
    val uriHandler = LocalUriHandler.current
    val annotated = remember(segments, color, fontWeight, fontStyle, uriHandler) {
        buildAnnotatedString {
            segments.forEach { segment ->
                val start = length
                val isLink = segment.link != null && safeLink(segment.link)
                withStyle(
                    SpanStyle(
                        fontWeight = if (segment.bold) FontWeight.Bold else fontWeight,
                        fontStyle = if (segment.italic) FontStyle.Italic else fontStyle,
                        fontFamily = if (segment.code) Mono else null,
                        color = if (segment.code) BlueInk else color,
                        background = if (segment.code) Surface2 else Color.Unspecified,
                        textDecoration = if (isLink) TextDecoration.Underline else null
                    )
                ) {
                    append(segment.text)
                }
                if (isLink) {
                    val url = segment.link.orEmpty()
                    addLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(color = BlueInk, textDecoration = TextDecoration.Underline)
                            ),
                            linkInteractionListener = {
                                // Unknown or malformed targets stay inside the app instead of crashing.
                                runCatching { uriHandler.openUri(url) }
                            }
                        ),
                        start = start,
                        end = length
                    )
                }
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = color,
        fontWeight = fontWeight,
        fontStyle = fontStyle
    )
}
