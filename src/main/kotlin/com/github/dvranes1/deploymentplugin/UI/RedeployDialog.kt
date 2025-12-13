package com.github.dvranes1.deploymentplugin.UI

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class RedeployDialog(private val project: Project) : DialogWrapper(project) {

    private val commits = listOf(
        "abc1234  |  Fix build pipeline",
        "9fd2210  |  Add health endpoint",
        "44a0b2c  |  Initial deploy"
    )

    private val list = JBList(commits)

    init {
        title = "Redeploy previous commit"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
            add(JBScrollPane(list), BorderLayout.CENTER)
        }
    }

    override fun doOKAction() {
        val selected = list.selectedValue ?: return
        // Demo: kasnije zoveš DeployService.redeploy(selectedCommit)
        super.doOKAction()
    }
}
