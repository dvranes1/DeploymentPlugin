package com.github.dvranes1.deploymentplugin.toolwindow

import com.github.dvranes1.deploymentplugin.auth.VercelAuthService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import com.intellij.openapi.components.service

internal class AuthPanel(
    private val project: Project,
    private val onAuthenticated: () -> Unit
) : JBPanel<AuthPanel>(BorderLayout()) {

    private val authService = project.service<VercelAuthService>()

    private val status = JBLabel("You are not authenticated with Vercel.")
    private val button = JButton("Authenticate with Vercel")

    init {
        border = JBUI.Borders.empty(12)

        val text = JBTextArea(
            "To use Deploy features, sign in with Vercel.\n" +
                    "A browser window will open. After you finish login, return to WebStorm."
        ).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            background = null
        }

        val actions = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(button)
        }

        add(status, BorderLayout.NORTH)
        add(text, BorderLayout.CENTER)
        add(actions, BorderLayout.SOUTH)

        button.addActionListener {
            button.isEnabled = false
            status.text = "Opening browser…"

            authService.startOAuthLoginAsync(
                project = project,
                onSuccess = {
                    button.isEnabled = true
                    status.text = "Authenticated ✅"
                    onAuthenticated()
                },
                onError = { t ->
                    button.isEnabled = true
                    status.text = "Authentication failed ❌"
                    Messages.showErrorDialog(project, t.message ?: t.toString(), "Vercel Authentication")
                }
            )
        }
    }
}
