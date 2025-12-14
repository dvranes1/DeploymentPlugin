package com.github.dvranes1.deploymentplugin.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

object SuggestionUI {
    fun showSuggestion(project: Project, text: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showDialog(
                project,
                text,
                "AI Error Fix Suggestion",
                arrayOf("Close"),
                0,
                Messages.getInformationIcon()
            )
        }
    }
}
