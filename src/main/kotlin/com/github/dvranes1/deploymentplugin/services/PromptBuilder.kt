package com.github.dvranes1.deploymentplugin.services

import com.github.dvranes1.deploymentplugin.errors.StackFrame
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager

object PromptBuilder {

    fun build(project: Project, top: StackFrame, frames: List<StackFrame>): String {
        val vFile = LocalFileSystem.getInstance().findFileByPath(top.filePath)
        val psi = vFile?.let { PsiManager.getInstance(project).findFile(it) }
        val doc = psi?.let { PsiDocumentManager.getInstance(project).getDocument(it) }

        val code = doc?.text ?: "<file not found>"

        val context = extractContextAroundLine(code, top.line)

        return """
            You are a senior engineer here is the code context from a stack trace of a runtime or a build error.
            Explain why the error occured briefly and how to fix it also briefly. Write at most 400 characters.

            Stacktrace:
            ${frames.joinToString("\n") { it.rawLine }}

            Top frame:
            File: ${top.filePath}
            Line: ${top.line}

            Code context:
            $context

            Please explain:
            1. Root cause
            2. What likely went wrong
            3. How to fix it
            4. A corrected code example
            
        """.trimIndent()
    }

    private fun extractContextAroundLine(code: String, line: Int, radius: Int = 10): String {
        val lines = code.lines()
        if (lines.isEmpty()) return ""

        val safeLine1 = line.coerceIn(1, lines.size)

        val start0 = (safeLine1 - radius - 1).coerceAtLeast(0)
        val endExcl = (safeLine1 + radius).coerceAtMost(lines.size)

        if (start0 >= endExcl) return "${safeLine1}: ${lines[safeLine1 - 1]}"

        return lines.subList(start0, endExcl)
            .mapIndexed { i, content -> "${start0 + i + 1}: $content" }
            .joinToString("\n")
    }

}