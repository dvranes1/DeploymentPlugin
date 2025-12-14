package com.github.dvranes1.deploymentplugin.toolwindow

import com.github.dvranes1.deploymentplugin.vercel.VercelDeployment
import com.github.dvranes1.deploymentplugin.vercel.VercelProject
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.ListSelectionModel

internal class ProjectDeploySelectorPanel(
    private val onProjectSelected: (VercelProject) -> Unit,
    private val onDeploymentActivated: (VercelDeployment) -> Unit,
) : JBPanel<ProjectDeploySelectorPanel>(BorderLayout()) {

    private val projectsModel = DefaultListModel<VercelProject>()
    private val deploysModel = DefaultListModel<VercelDeployment>()

    private val projectsList = JBList(projectsModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        addListSelectionListener {
            val p = selectedValue ?: return@addListSelectionListener
            onProjectSelected(p)
        }
    }

    private val deploysList = JBList(deploysModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION

        // Double-click => start playback
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    selectedValue?.let(onDeploymentActivated)
                }
            }
        })

        // Enter => start playback
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "play")
        actionMap.put("play", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                selectedValue?.let(onDeploymentActivated)
            }
        })
    }

    private val searchField = JBTextField().apply {
        emptyText.text = "Filter projects…"
        document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = filter()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = filter()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = filter()
        })
    }

    private var allProjects: List<VercelProject> = emptyList()

    init {
        val ui: JComponent = panel {
            row { cell(searchField).align(AlignX.FILL) }
            group("Projects") {
                row {
                    cell(JBScrollPane(projectsList)).align(AlignX.FILL)
                }.resizableRow()
            }
            group("Deployments") {
                row {
                    cell(JBScrollPane(deploysList)).align(AlignX.FILL)
                }.resizableRow()
            }
        }

        add(ui, BorderLayout.CENTER)
    }

    fun setProjects(projects: List<VercelProject>) {
        allProjects = projects
        filter()
    }

    fun setDeployments(deployments: List<VercelDeployment>) {
        deploysModel.clear()
        deployments.forEach { deploysModel.addElement(it) }
        if (deploysModel.size() > 0) deploysList.selectedIndex = 0
    }

    fun getSelectedProjectOrNull(): VercelProject? = projectsList.selectedValue
    fun getSelectedDeploymentOrNull(): VercelDeployment? = deploysList.selectedValue

    private fun filter() {
        val q = searchField.text.trim().lowercase()
        projectsModel.clear()

        val filtered = if (q.isBlank()) allProjects
        else allProjects.filter { (it.name ?: "").lowercase().contains(q) || (it.id ?: "").contains(q) }

        filtered.forEach { projectsModel.addElement(it) }
        if (projectsModel.size() > 0) projectsList.selectedIndex = 0
    }
}
