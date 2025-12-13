package com.github.dvranes1.deploymentplugin.UI

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil

data class ErrorLocation(
    val filePath: String,
    val line1: Int,
    val col1: Int,
    val message: String
)

data class GeneralErrorResult(
    val message: String,
    val clickableRangeInLine: IntRange
)

data class ParseResult(
    val loc: ErrorLocation,
    val clickableRangeInLine: IntRange
)

interface AiClient {
    fun suggestFix(
        filePath: String,
        line: Int,
        col: Int,
        errorMessage: String,
        context: String?
    ): String
}

private fun parseGeneralError(line: String): GeneralErrorResult? {
    val trimmed = line.trim()
    if (trimmed.isBlank()) return null

    // Ignoriši normalne logove
    if (trimmed.startsWith("LOG:") || trimmed.startsWith("INFO:") || trimmed.startsWith("DEBUG:")) return null

    // Heuristika: “error-ish” ključne reči (dodaj po potrebi)
    val isErrorish = Regex(
        """(?i)\b(error|failed|failure|exception|unauthorized|forbidden|timeout|timed out|out of memory|oom|killed|terminated|exit code|command failed|cannot|unable)\b"""
    ).containsMatchIn(trimmed)

    if (!isErrorish) return null

    // Klikabilan ceo red (možeš suziti kasnije)
    return GeneralErrorResult(
        message = trimmed,
        clickableRangeInLine = 0..(line.length - 1).coerceAtLeast(0)
    )
}


class DemoAiClient : AiClient {
    override fun suggestFix(
        filePath: String,
        line: Int,
        col: Int,
        errorMessage: String,
        context: String?
    ): String = buildString {
        appendLine("AI suggestion (DEMO)")
        appendLine("File: $filePath")
        appendLine("At: $line:$col")
        appendLine()
        appendLine("Error: $errorMessage")
        if (!context.isNullOrBlank()) {
            appendLine()
            appendLine("Context:")
            appendLine(context)
        }
        appendLine()
        appendLine("Try: check syntax around this spot; remove/replace unexpected token.")
    }
}

/**
 * Filter koji:
 * - parsira razne formate errora
 * - pravi hyperlink u ConsoleView
 * - na klik: otvara fajl (ako postoji) + async poziva AI + vraća suggestion kroz callback
 */
class DeployErrorHyperlinkFilter(
    private val project: Project,
    private val aiClient: AiClient,
    private val onOpenLocation: (file: VirtualFile?, line0: Int, col0: Int) -> Unit,
    private val onAiSuggestionReady: (file: VirtualFile?, line0: Int, col0: Int, suggestion: String) -> Unit
) : Filter {

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val parsed = parseAnyError(line)

        if (parsed != null) {
            val lineStartOffset = entireLength - line.length
            val startOffset = lineStartOffset + parsed.clickableRangeInLine.first
            val endOffset = lineStartOffset + parsed.clickableRangeInLine.last + 1

            val hyperlink = HyperlinkInfo { _ ->
                val loc = parsed.loc
                val file = resolveFile(project, loc.filePath)

                val line0 = (loc.line1 - 1).coerceAtLeast(0)
                val col0 = (loc.col1 - 1).coerceAtLeast(0)

                if (file != null) {
                    OpenFileDescriptor(project, file, line0, col0).navigate(true)
                }
                onOpenLocation(file, line0, col0)

                AppExecutorUtil.getAppExecutorService().submit {
                    val context = if (file != null) extractContext(file, loc.line1) else null
                    val suggestion = aiClient.suggestFix(
                        filePath = loc.filePath,
                        line = loc.line1,
                        col = loc.col1,
                        errorMessage = loc.message,
                        context = context
                    )
                    ApplicationManager.getApplication().invokeLater {
                        onAiSuggestionReady(file, line0, col0, suggestion)
                    }
                }
            }

            return Filter.Result(listOf(Filter.ResultItem(startOffset, endOffset, hyperlink)))
        }

        // CASE 2: opšta greška (nema file/line/col) -> klik => AI bubble u toolwindow-u
        val general = parseGeneralError(line) ?: return null

        val lineStartOffset = entireLength - line.length
        val startOffset = lineStartOffset + general.clickableRangeInLine.first
        val endOffset = lineStartOffset + general.clickableRangeInLine.last + 1

        val hyperlink = HyperlinkInfo { _ ->
            onOpenLocation(null, 0, 0)

            AppExecutorUtil.getAppExecutorService().submit {
                val suggestion = aiClient.suggestFix(
                    filePath = "<no-file>",
                    line = 0,
                    col = 0,
                    errorMessage = general.message,
                    context = null
                )
                ApplicationManager.getApplication().invokeLater {
                    onAiSuggestionReady(null, 0, 0, suggestion)
                }
            }
        }

        return Filter.Result(listOf(Filter.ResultItem(startOffset, endOffset, hyperlink)))
    }
}


fun parseAnyError(line: String): ParseResult? {
    parseStackTrace(line)?.let { return it }        // at ... (path:line:col)
    parseParenLineCol(line)?.let { return it }      // path(line,col): msg
    parseEslint(line)?.let { return it }            // path:line:col  error  msg
    parseColonLineCol(line)?.let { return it }
    return null
}

private fun parseColonLineCol(line: String): ParseResult? {
    val m = Regex(""":(\d+):(\d+)""").findAll(line).lastOrNull() ?: return null
    val line1 = m.groupValues[1].toIntOrNull() ?: return null
    val col1 = m.groupValues[2].toIntOrNull() ?: return null

    val before = line.substring(0, m.range.first).trim()
        .removePrefix("ERROR:").trim()

    if (before.isBlank()) return null

    val msg = line.substring(m.range.last + 1).trim().removePrefix("-").trim()
        .ifBlank { "Unknown error" }

    val clickable = "$before:$line1:$col1"
    val start = line.indexOf(clickable).takeIf { it >= 0 } ?: line.indexOf(before).takeIf { it >= 0 } ?: return null
    val end = start + clickable.length - 1

    return ParseResult(ErrorLocation(before, line1, col1, msg), start..end)
}

private fun parseParenLineCol(line: String): ParseResult? {
    val r = Regex("""(.+)\((\d+),\s*(\d+)\):\s*(.+)""")
    val m = r.find(line) ?: return null

    val path = m.groupValues[1].trim().removePrefix("ERROR:").trim()
    val line1 = m.groupValues[2].toIntOrNull() ?: return null
    val col1 = m.groupValues[3].toIntOrNull() ?: return null
    val msg = m.groupValues[4].trim().ifBlank { "Unknown error" }

    val clickable = "${path}(${line1},${col1})"
    val start = line.indexOf(clickable).takeIf { it >= 0 } ?: line.indexOf(path).takeIf { it >= 0 } ?: return null
    val end = start + clickable.length - 1

    return ParseResult(ErrorLocation(path, line1, col1, msg), start..end)
}

private fun parseStackTrace(line: String): ParseResult? {
    // hvata ceo sadržaj u zagradi: ( ... )
    val paren = Regex("""\(([^)]*)\)""").find(line) ?: return null
    val inside = paren.groupValues[1] // npr. C:\...\index.js:14:10

    // nađi poslednje ":<line>:<col>" unutar inside (radi i za Windows drive "C:")
    val lc = Regex(""":(\d+):(\d+)$""").find(inside) ?: return null
    val line1 = lc.groupValues[1].toIntOrNull() ?: return null
    val col1 = lc.groupValues[2].toIntOrNull() ?: return null

    val path = inside.substring(0, lc.range.first).trim() // pre ":14:10"

    // clickable range: obuhvati CELO "(inside)" uključujući zagrade -> nema više crvene ")"
    val start = paren.range.first
    val end = paren.range.last

    return ParseResult(
        ErrorLocation(path, line1, col1, line.trim()),
        start..end
    )
}


private fun parseEslint(line: String): ParseResult? {
    val r = Regex("""(.+):(\d+):(\d+)\s+(error|warning)\s+(.+)""", RegexOption.IGNORE_CASE)
    val m = r.find(line) ?: return null

    val path = m.groupValues[1].trim()
    val line1 = m.groupValues[2].toIntOrNull() ?: return null
    val col1 = m.groupValues[3].toIntOrNull() ?: return null
    val msg = m.groupValues[5].trim().ifBlank { "Unknown error" }

    val clickable = "$path:$line1:$col1"
    val start = line.indexOf(clickable).takeIf { it >= 0 } ?: line.indexOf(path).takeIf { it >= 0 } ?: return null
    val end = start + clickable.length - 1

    return ParseResult(ErrorLocation(path, line1, col1, msg), start..end)
}

/* ----------------------- File resolving + context ----------------------- */

private fun resolveFile(project: Project, rawPath: String): VirtualFile? {
    val lfs = LocalFileSystem.getInstance()
    val path = rawPath.removePrefix("file://").replace('\\', '/').trim()

    // 1) apsolutno
    lfs.findFileByPath(path)?.let { return it }

    // 2) relativno na basePath
    val base = project.basePath?.replace('\\', '/') ?: return null
    lfs.findFileByPath("$base/$path")?.let { return it }

    return null
}

private fun extractContext(file: VirtualFile, line1: Int, radius: Int = 3): String? {
    val doc = FileDocumentManager.getInstance().getDocument(file) ?: return null
    if (doc.lineCount == 0) return null

    val target0 = (line1 - 1).coerceIn(0, doc.lineCount - 1)
    val from = (target0 - radius).coerceAtLeast(0)
    val to = (target0 + radius).coerceAtMost(doc.lineCount - 1)

    return buildString {
        for (i in from..to) {
            val start = doc.getLineStartOffset(i)
            val end = doc.getLineEndOffset(i)
            val text = doc.charsSequence.subSequence(start, end).toString()
            val marker = if (i == target0) ">>" else "  "
            append(String.format("%s %4d | %s\n", marker, i + 1, text))
        }
    }
}
