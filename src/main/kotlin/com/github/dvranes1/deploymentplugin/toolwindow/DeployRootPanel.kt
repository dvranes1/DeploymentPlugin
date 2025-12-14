package com.github.dvranes1.deploymentplugin.toolwindow

import com.github.dvranes1.deploymentplugin.auth.VercelAuthService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger

internal class DeployRootPanel(private val project: Project) : JBPanel<DeployRootPanel>(BorderLayout()) {
    private val cards = JPanel(CardLayout())

    private val deployPanel = DeployToolWindowPanel(project)
    private val authPanel = AuthPanel(project, onAuthenticated = { showDeploy() })

    private val authService = project.service<VercelAuthService>()

    init {
        cards.add(authPanel, "AUTH")
        cards.add(deployPanel, "DEPLOY")
        add(cards, BorderLayout.CENTER)

    }

    fun refresh() {
        AppExecutorUtil.getAppExecutorService().submit {
            val ok = authService.isAuthenticated()
            ApplicationManager.getApplication().invokeLater {
                //thisLogger().warn("AUTH check ok=$ok token=${authService.getAccessTokenOrNull()?.take(8)}")

                if (ok) showDeploy() else showAuth()
            }
        }
    }

    private fun showAuth() {
        (cards.layout as CardLayout).show(cards, "AUTH")
    }

    private fun showDeploy() {
        (cards.layout as CardLayout).show(cards, "DEPLOY")
    }
}
