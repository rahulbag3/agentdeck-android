package com.agentdeck.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentdeck.app.ui.theme.Ink
import com.agentdeck.app.ui.theme.Line
import com.agentdeck.app.ui.theme.Mono
import com.agentdeck.app.ui.theme.Muted2
import com.agentdeck.app.ui.theme.Surface2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

internal data class ReplyFile(val name: String, val mime: String, val bytes: ByteArray)

internal class ReplyExporter(
    private val launch: (ReplyFile) -> Unit,
    private val toast: (String) -> Unit
) {
    fun save(file: ReplyFile) = launch(file)
    fun notify(message: String) = toast(message)
}

internal val LocalReplyExporter = androidx.compose.runtime.staticCompositionLocalOf<ReplyExporter?> { null }

@Composable
internal fun rememberReplyExporter(onToast: (String) -> Unit): ReplyExporter {
    val context = LocalContext.current
    val currentToast = rememberUpdatedState(onToast)
    val pending = remember { mutableStateOf<ReplyFile?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val file = pending.value
        pending.value = null
        if (result.resultCode != Activity.RESULT_OK || file == null) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val outcome = runCatching {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: error("The selected file could not be opened")
            stream.use { it.write(file.bytes) }
        }
        currentToast.value(
            outcome.fold(
                onSuccess = { "Saved ${file.name}" },
                onFailure = { "Could not save ${file.name}" }
            )
        )
    }
    return remember(context, launcher) {
        ReplyExporter(
            launch = { file ->
                pending.value = file
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = file.mime
                    putExtra(Intent.EXTRA_TITLE, file.name)
                }
                runCatching { launcher.launch(intent) }
                    .onFailure {
                        pending.value = null
                        currentToast.value("No file picker on this device")
                    }
            },
            toast = { currentToast.value(it) }
        )
    }
}

internal fun replyFileBase(text: String): String {
    val lines = text.lineSequence().map { it.trim() }.toList()
    val heading = lines.firstOrNull { it.matches(Regex("^#{1,6}\\s+\\S.*")) }
    val line = heading
        ?: lines.firstOrNull {
            it.isNotBlank() && !it.startsWith("```") && !it.startsWith("<") &&
                !it.startsWith("{") && !it.startsWith("[")
        }
        ?: lines.firstOrNull { it.isNotBlank() && !it.startsWith("```") }
        ?: ""
    val cleaned = line
        .replace(Regex("[#>*_`\\[\\]()]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(48)
    val slug = cleaned.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
        .trim('-')
        .take(48)
        .trim('-')
    return slug.ifBlank { "agentdeck-reply" }
}

internal fun markdownReplyFile(text: String, base: String): ReplyFile =
    ReplyFile("$base.md", "text/markdown", text.toByteArray(Charsets.UTF_8))

internal fun textReplyFile(text: String, base: String): ReplyFile =
    ReplyFile("$base.txt", "text/plain", text.toByteArray(Charsets.UTF_8))

internal fun htmlReplyFile(text: String, base: String): ReplyFile =
    ReplyFile("$base.html", "text/html", htmlDocument(base, text).toByteArray(Charsets.UTF_8))

internal fun pdfReplyFile(text: String, base: String): ReplyFile =
    ReplyFile("$base.pdf", "application/pdf", replyPdf(text, base))

internal fun codeReplyFile(language: String, code: String, base: String): ReplyFile {
    val type = codeType(language, code)
    return ReplyFile("$base.${type.extension}", type.mime, code.toByteArray(Charsets.UTF_8))
}

private data class CodeType(val extension: String, val mime: String)

private fun codeType(language: String, code: String): CodeType {
    val trimmed = code.trimStart()
    var lang = language.lowercase(Locale.ROOT).removePrefix("language-").trim()
    if (lang in setOf("code", "text", "plaintext", "plain")) lang = ""
    if (lang.isEmpty()) {
        lang = when {
            trimmed.startsWith("<!doctype html", ignoreCase = true) ||
                trimmed.startsWith("<html", ignoreCase = true) -> "html"
            trimmed.startsWith("<svg", ignoreCase = true) -> "svg"
            trimmed.startsWith("<?xml", ignoreCase = true) -> "xml"
            (trimmed.startsWith("{") && trimmed.trimEnd().endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.trimEnd().endsWith("]")) -> "json"
            else -> ""
        }
    }
    return knownCodeTypes[lang] ?: run {
        val safe = lang.filter { it.isLetterOrDigit() }.take(10)
        if (safe.isEmpty()) CodeType("txt", "text/plain") else CodeType(safe, "text/plain")
    }
}

private val knownCodeTypes: Map<String, CodeType> = buildMap {
    fun add(ext: String, mime: String, vararg aliases: String) {
        val type = CodeType(ext, mime)
        put(ext, type)
        aliases.forEach { put(it, type) }
    }
    add("html", "text/html", "htm", "xhtml")
    add("svg", "image/svg+xml")
    add("json", "application/json")
    add("xml", "text/xml")
    add("css", "text/css")
    add("scss", "text/plain", "sass", "less")
    add("csv", "text/csv")
    add("tsv", "text/tab-separated-values")
    add("md", "text/markdown", "markdown")
    add("kt", "text/plain", "kotlin", "kts")
    add("java", "text/plain")
    add("js", "text/javascript", "javascript")
    add("jsx", "text/plain")
    add("ts", "text/plain", "typescript")
    add("tsx", "text/plain")
    add("py", "text/x-python", "python")
    add("rb", "text/plain", "ruby")
    add("go", "text/plain", "golang")
    add("rs", "text/plain", "rust")
    add("swift", "text/plain")
    add("c", "text/plain")
    add("h", "text/plain")
    add("cpp", "text/plain", "c++", "cxx", "cc")
    add("cs", "text/plain", "csharp", "c#")
    add("php", "text/plain")
    add("sql", "text/plain")
    add("sh", "text/x-sh", "bash", "shell", "zsh")
    add("ps1", "text/plain", "powershell")
    add("bat", "text/plain", "batch", "cmd")
    add("yaml", "text/plain", "yml")
    add("toml", "text/plain")
    add("ini", "text/plain", "conf", "properties")
    add("gradle", "text/plain")
    add("dockerfile", "text/plain", "docker")
    add("diff", "text/plain", "patch")
    add("log", "text/plain")
    add("txt", "text/plain")
}

// ----- HTML export -----

internal fun htmlDocument(title: String, source: String): String {
    val body = StringBuilder()
    parseMarkdown(source).forEach { block ->
        when (block) {
            is MarkdownBlock.Paragraph -> body.append("<p>").append(inlineHtml(block.text)).append("</p>\n")
            is MarkdownBlock.Heading -> {
                val level = block.level.coerceIn(1, 6)
                body.append("<h$level>").append(inlineHtml(block.text)).append("</h$level>\n")
            }
            is MarkdownBlock.BulletList -> {
                body.append("<ul>\n")
                block.items.forEach { body.append("<li>").append(inlineHtml(it)).append("</li>\n") }
                body.append("</ul>\n")
            }
            is MarkdownBlock.NumberedList -> {
                body.append("<ol start=\"").append(block.start.coerceAtLeast(1)).append("\">\n")
                block.items.forEach { body.append("<li>").append(inlineHtml(it)).append("</li>\n") }
                body.append("</ol>\n")
            }
            is MarkdownBlock.CodeFence -> {
                val language = block.language.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                val classAttr = if (language.isBlank()) "" else " class=\"language-${escapeHtml(language)}\""
                body.append("<pre><code$classAttr>")
                    .append(escapeHtml(block.code))
                    .append("</code></pre>\n")
            }
            is MarkdownBlock.Quote -> body.append("<blockquote><p>")
                .append(inlineHtml(block.text.replace('\n', ' ')))
                .append("</p></blockquote>\n")
            MarkdownBlock.Rule -> body.append("<hr>\n")
        }
    }
    return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(title)}</title>
<style>
:root { color-scheme: light dark; }
body { margin: 0 auto; max-width: 760px; padding: 32px 20px 64px; color: #1c1e21; background: #ffffff;
  font: 16px/1.62 -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; }
h1, h2, h3, h4, h5, h6 { line-height: 1.25; margin: 1.4em 0 0.5em; }
h1 { font-size: 1.7em; } h2 { font-size: 1.35em; } h3 { font-size: 1.15em; }
p, ul, ol, blockquote { margin: 0.65em 0; }
ul, ol { padding-left: 1.4em; }
li { margin: 0.25em 0; }
blockquote { margin-left: 0; padding: 2px 0 2px 14px; border-left: 3px solid #c6c9cf; color: #4a4e55; font-style: italic; }
hr { border: 0; border-top: 1px solid #d8dade; margin: 1.4em 0; }
code { font: 0.88em "SF Mono", "Cascadia Mono", Menlo, Consolas, monospace; background: #f1f2f4; border-radius: 4px; padding: 1px 4px; }
pre { background: #f6f7f8; border: 1px solid #e2e4e8; border-radius: 9px; padding: 14px; overflow-x: auto; }
pre code { background: none; padding: 0; font-size: 0.85em; line-height: 1.5; }
a { color: #2563eb; }
@media (prefers-color-scheme: dark) {
  body { color: #e8eaed; background: #0a0c0e; }
  blockquote { border-left-color: #3a3f46; color: #aeb4bc; }
  hr { border-top-color: #2a2f35; }
  code { background: #191d21; }
  pre { background: #14181c; border-color: #262b31; }
  a { color: #9cbdff; }
}
</style>
</head>
<body>
$body</body>
</html>
"""
}

private fun inlineHtml(source: String): String {
    val out = StringBuilder()
    parseInline(source).forEach { segment ->
        var html = escapeHtml(segment.text)
        if (segment.code) html = "<code>$html</code>"
        if (segment.italic) html = "<em>$html</em>"
        if (segment.bold) html = "<strong>$html</strong>"
        val link = segment.link
        if (link != null && safeLink(link)) html = "<a href=\"${escapeHtml(link)}\">$html</a>"
        out.append(html)
    }
    return out.toString()
}

private fun escapeHtml(value: String): String = buildString(value.length) {
    value.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}

// ----- PDF export -----

internal fun replyPdf(source: String, title: String): ByteArray {
    val writer = PdfWriter(title)
    writer.start()
    parseMarkdown(source).forEach { block ->
        when (block) {
            is MarkdownBlock.Paragraph -> writer.text(block.text)
            is MarkdownBlock.Heading -> writer.heading(block.text, block.level)
            is MarkdownBlock.BulletList -> block.items.forEach { writer.listItem("•", it) }
            is MarkdownBlock.NumberedList -> block.items.forEachIndexed { index, item ->
                writer.listItem("${block.start + index}.", item)
            }
            is MarkdownBlock.CodeFence -> writer.code(block.code)
            is MarkdownBlock.Quote -> writer.quote(block.text)
            MarkdownBlock.Rule -> writer.rule()
        }
    }
    return writer.finish()
}

private class PdfWriter(private val title: String) {
    companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48f
        const val CONTENT_WIDTH = (PAGE_WIDTH - MARGIN * 2).toInt()
    }

    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var cursor = MARGIN
    private var pageIndex = 0

    private val ink = android.graphics.Color.rgb(28, 30, 33)
    private val gray = android.graphics.Color.rgb(110, 114, 122)
    private val bodyPaint = TextPaint().apply {
        textSize = 10.5f
        color = ink
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        isAntiAlias = true
    }
    private val headingPaint = TextPaint(bodyPaint).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val monoPaint = TextPaint().apply {
        textSize = 8.8f
        color = ink
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }
    private val quotePaint = TextPaint(bodyPaint).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        color = android.graphics.Color.rgb(74, 78, 85)
    }
    private val metaPaint = TextPaint().apply {
        textSize = 8f
        color = gray
        typeface = Typeface.SANS_SERIF
        isAntiAlias = true
    }

    fun start() {
        newPage()
    }

    fun finish(): ByteArray {
        page?.let { document.finishPage(it) }
        page = null
        canvas = null
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    fun heading(text: String, level: Int) {
        val paint = TextPaint(headingPaint).apply {
            textSize = when (level) {
                1 -> 16.5f
                2 -> 13.8f
                3 -> 12f
                else -> 11f
            }
        }
        drawBlock(text, paint, padTop = if (level == 1) 10f else 8f, padBottom = 2f, gap = 5f, background = null, rich = true)
    }

    fun text(text: String) {
        drawBlock(text, bodyPaint, padTop = 0f, padBottom = 0f, gap = 7f, background = null, rich = true)
    }

    fun listItem(marker: String, text: String) {
        drawBlock("$marker  $text", bodyPaint, padTop = 0f, padBottom = 0f, gap = 4f, background = null, rich = true)
    }

    fun quote(text: String) {
        drawBlock(text.replace('\n', ' '), quotePaint, padTop = 2f, padBottom = 2f, gap = 8f, background = null, rich = true)
    }

    fun code(code: String) {
        val text = code.trimEnd().ifBlank { " " }
        drawBlock(
            text,
            monoPaint,
            padTop = 8f,
            padBottom = 8f,
            gap = 9f,
            background = android.graphics.Color.rgb(245, 246, 248),
            rich = false
        )
    }

    fun rule() {
        if (cursor + 14f > PAGE_HEIGHT - MARGIN) newPage()
        canvas?.drawRect(MARGIN, cursor + 5f, PAGE_WIDTH - MARGIN, cursor + 6f, Paint().apply {
            color = android.graphics.Color.rgb(216, 218, 222)
        })
        cursor += 16f
    }

    private fun newPage() {
        page?.let { document.finishPage(it) }
        pageIndex += 1
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
        val fresh = document.startPage(info)
        page = fresh
        canvas = fresh.canvas.apply { drawColor(android.graphics.Color.WHITE) }
        cursor = MARGIN
        val footer = title.take(70).ifBlank { "AgentDeck reply" }
        fresh.canvas.drawText("$footer  ·  $pageIndex", MARGIN, PAGE_HEIGHT - 26f, metaPaint)
    }

    private fun layoutOf(source: CharSequence, paint: TextPaint, spacing: Float): StaticLayout =
        StaticLayout.Builder.obtain(source, 0, source.length, paint, CONTENT_WIDTH - 16)
            .setIncludePad(false)
            .setLineSpacing(0f, spacing)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()

    private fun drawBlock(
        raw: String,
        paint: TextPaint,
        padTop: Float,
        padBottom: Float,
        gap: Float,
        background: Int?,
        rich: Boolean
    ) {
        var remaining = raw.replace("\r\n", "\n").trimEnd()
        if (remaining.isEmpty()) return
        val lineHeight = paint.fontMetrics.let { it.descent - it.ascent }
        val pageBottom = PAGE_HEIGHT - MARGIN
        val charWidth = paint.measureText("n").coerceAtLeast(1f)
        val charsPerLine = ((CONTENT_WIDTH - 16) / charWidth).toInt().coerceAtLeast(1)

        while (remaining.isNotEmpty()) {
            if (cursor + padTop + padBottom + lineHeight > pageBottom) newPage()
            val available = (pageBottom - cursor - padTop - padBottom).coerceAtLeast(lineHeight)
            val maxLines = (available / lineHeight).toInt().coerceAtLeast(1)

            // Cap every probe to roughly one page of text. Measuring the
            // whole reply at once made long code blocks blow up memory.
            var take = minOf(remaining.length, charsPerLine * maxLines)
            if (take < remaining.length) {
                val newline = remaining.lastIndexOf('\n', take)
                val space = remaining.lastIndexOf(' ', take)
                take = when {
                    newline >= take / 2 -> newline + 1
                    space >= take / 2 -> space + 1
                    else -> take
                }.coerceAtLeast(1)
            }
            var chunk = remaining.substring(0, take)
            var rendered = if (rich) styledSpans(chunk) else chunk
            var layout = layoutOf(rendered, paint, 1.24f)

            while (layout.height > available && chunk.length > 1) {
                var cut = (chunk.length * 3) / 4
                val newline = chunk.lastIndexOf('\n', cut)
                val space = chunk.lastIndexOf(' ', cut)
                cut = when {
                    newline >= cut / 2 -> newline + 1
                    space >= cut / 2 -> space + 1
                    else -> cut
                }.coerceAtLeast(1)
                chunk = chunk.substring(0, cut)
                rendered = if (rich) styledSpans(chunk) else chunk
                layout = layoutOf(rendered, paint, 1.24f)
            }

            val boxHeight = layout.height + padTop + padBottom
            if (cursor + boxHeight > pageBottom) {
                newPage()
                continue
            }
            if (background != null) {
                canvas?.drawRoundRect(
                    MARGIN, cursor, PAGE_WIDTH - MARGIN, cursor + boxHeight, 6f, 6f,
                    Paint().apply {
                        color = background
                        style = Paint.Style.FILL
                    }
                )
            }
            canvas?.save()
            canvas?.translate(MARGIN + 8f, cursor + padTop)
            layout.draw(canvas)
            canvas?.restore()
            cursor += boxHeight + gap
            remaining = remaining.substring(chunk.length).trimStart('\n')
        }
    }

    private fun styledSpans(source: String): CharSequence {
        val builder = SpannableStringBuilder()
        parseInline(source).forEach { segment ->
            val start = builder.length
            builder.append(segment.text)
            val end = builder.length
            if (segment.bold) {
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (segment.italic) {
                builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (segment.code) {
                builder.setSpan(
                    BackgroundColorSpan(android.graphics.Color.rgb(241, 242, 244)),
                    start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return builder
    }
}

// ----- Save menu -----

@Composable
internal fun ReplySaveMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    exporter: ReplyExporter,
    text: String,
    base: String
) {
    val scope = rememberCoroutineScope()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(10.dp),
        containerColor = Surface2,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                "SAVE REPLY",
                fontFamily = Mono,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Muted2
            )
        }
        SaveMenuItem("PDF document", "pdf") {
            onDismiss()
            scope.launch {
                exporter.notify("Building PDF…")
                val file = withContext(Dispatchers.Default) { runCatching { pdfReplyFile(text, base) } }
                file.onSuccess(exporter::save).onFailure { exporter.notify("Could not build the PDF") }
            }
        }
        SaveMenuItem("Web page (.html)", "html") {
            onDismiss()
            scope.launch {
                val file = withContext(Dispatchers.Default) { runCatching { htmlReplyFile(text, base) } }
                file.onSuccess(exporter::save).onFailure { exporter.notify("Could not build the page") }
            }
        }
        SaveMenuItem("Markdown (.md)", "md") {
            onDismiss()
            exporter.save(markdownReplyFile(text, base))
        }
        SaveMenuItem("Plain text (.txt)", "txt") {
            onDismiss()
            exporter.save(textReplyFile(text, base))
        }
    }
}

@Composable
private fun SaveMenuItem(label: String, extension: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                fontSize = 11.5.sp,
                color = Ink
            )
        },
        trailingIcon = {
            Text(
                extension.uppercase(Locale.ROOT),
                fontFamily = Mono,
                fontSize = 8.sp,
                color = Muted2
            )
        },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    )
}
