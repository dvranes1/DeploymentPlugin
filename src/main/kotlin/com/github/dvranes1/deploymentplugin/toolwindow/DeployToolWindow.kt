package com.github.dvranes1.deploymentplugin.toolwindow

import VercelApiClient
import com.github.dvranes1.deploymentplugin.auth.VercelAuthService
import com.github.dvranes1.deploymentplugin.errors.AiClient
import com.github.dvranes1.deploymentplugin.errors.DemoAiClient
import com.github.dvranes1.deploymentplugin.errors.DeployErrorHyperlinkFilter
import com.github.dvranes1.deploymentplugin.vercel.VercelDeploymentsService
import com.github.dvranes1.deploymentplugin.vercel.VercelLogsService
import com.github.dvranes1.deploymentplugin.vercel.VercelProjectsService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import com.github.dvranes1.deploymentplugin.vercel.*
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import java.awt.Component
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.ListSelectionModel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBList
import com.intellij.util.Alarm

class DeployToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val root = DeployRootPanel(project)
        val content = ContentFactory.getInstance().createContent(root, null, false)
        toolWindow.contentManager.addContent(content)
        root.refresh()

    }
}

internal class DeployToolWindowPanel(private val project: Project) : JBPanel<DeployToolWindowPanel>(BorderLayout()) {


    private val console = ConsoleViewImpl(project, /* viewer */ false)
    private val consoleComponent = console.component


    private val branchLabel = JBLabel("Branch: -")
    private val commitLabel = JBLabel("Commit: -")
    private val healthLabel = JBLabel("Health: ● Idle").apply {
        foreground = JBColor(0xF57F17, 0xFFD54F)
    }

    private val projectsButton = JButton("Projects")
    private val redeployButton = JButton("Redeploy")
    private val stopButton = JButton("Stop")

    private val auth = project.service<VercelAuthService>()

    private val api = VercelApiClient()
    private val projectsService = VercelProjectsService(api)
    private val deploymentsService = VercelDeploymentsService(api)
    private val logsService = VercelLogsService(api)

    private val aiClient: AiClient = DemoAiClient()

    private val stopFlag = AtomicBoolean(false)
    private var selectedProjectId: String? = null
    private var selectedDeploymentId: String? = null

    private val snapshotButton = JButton("Snapshot")

    init {
        console.addMessageFilter(
            DeployErrorHyperlinkFilter(
                project = project,
                aiClient = aiClient,
                onOpenLocation = { _, _, _ -> },
                onAiSuggestionReady = { file, line0, col0, suggestion ->
                    if (file != null) {
                        ApplicationManager.getApplication().invokeLater {
                            val descriptor = OpenFileDescriptor(project, file, line0, col0)
                            val editor = FileEditorManager.getInstance(project)
                                .openTextEditor(descriptor, true)
                                ?: return@invokeLater
                            showAiBalloonAtEditor(editor, line0, col0, suggestion)
                        }
                    } else {
                        showAiBalloonInToolWindow(suggestion)
                    }
                }
            )
        )

        val right = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            val top = JPanel(FlowLayout(FlowLayout.LEFT, 12, 6)).apply {
                add(branchLabel)
                add(commitLabel)
                add(healthLabel)
            }

            val bottom = JPanel(FlowLayout(FlowLayout.LEFT, 12, 6)).apply {
                add(projectsButton)
                add(redeployButton)
                add(stopButton)
                add(snapshotButton)
            }

            add(top, BorderLayout.NORTH)
            add(JBScrollPane(consoleComponent), BorderLayout.CENTER)
            add(bottom, BorderLayout.SOUTH)
        }

        add(right, BorderLayout.CENTER)

        projectsButton.addActionListener { loadProjects() }
        redeployButton.addActionListener { }
        stopButton.addActionListener { stopPlayback() }
        snapshotButton.addActionListener { snapshotBuildLogs() }
        redeployButton.addActionListener { redeployLikeFollowBuildLogs() }
        stopButton.addActionListener { stopPlayback() }

    }

    private fun loadProjects() {
        val token = auth.getAccessTokenOrNull()
        if (token == null) {
            console.print("ERROR: Not authenticated\n", ConsoleViewContentType.ERROR_OUTPUT)
            return
        }

        setHealth("Loading projects…", kind = "loading")

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val projects = projectsService.listProjects(token)

                ApplicationManager.getApplication().invokeLater {
                    setHealth("Pick project", kind = "idle")
                    showProjectsPopup(projects)
                }
            } catch (t: Throwable) {
                ApplicationManager.getApplication().invokeLater {
                    console.print("ERROR: Failed to load projects: ${t.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
                    setHealth("Error", kind = "error")
                }
            }
        }
    }

    private fun showProjectsPopup(projects: List<VercelProject>) {
        if (projects.isEmpty()) {
            console.print("INFO: No projects found\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            return
        }

        val list = JBList(projects).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    val p = value as VercelProject
                    text = p.name ?: p.id ?: "Unnamed project"
                    return c
                }
            }
        }

        PopupChooserBuilder(list)
            .setTitle("Select Vercel Project")
            .setItemChoosenCallback {
                val selected = list.selectedValue ?: return@setItemChoosenCallback
                loadDeploymentsAndShowPopup(selected)
            }
            .createPopup()
            .showUnderneathOf(projectsButton)
    }

    private fun showDeploymentsPopup(projectObj: VercelProject, deps: List<VercelDeployment>) {
        if (deps.isEmpty()) {
            console.print("INFO: No deployments for project ${projectObj.name}\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            return
        }

        val projectId = projectObj.id
        if (projectId.isNullOrBlank()) {
            console.print("ERROR: Project id is missing\n", ConsoleViewContentType.ERROR_OUTPUT)
            return
        }

        val list = JBList(deps).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    val d = value as VercelDeployment
                    val shortSha = (d.sha ?: "-").take(7)
                    val branch = d.branch ?: "-"
                    val state = d.state ?: "-"
                    text = "$branch • $shortSha • $state"
                    return c
                }
            }
        }

        PopupChooserBuilder(list)
            .setTitle("Select Deployment (${projectObj.name ?: projectObj.id})")
            .setItemChoosenCallback {
                val selected = list.selectedValue ?: return@setItemChoosenCallback
                val depId = selected.id ?: return@setItemChoosenCallback
                selectedProjectId = projectId
                selectedDeploymentId = depId

                playDeployment(projectId, depId)

            }
            .createPopup()
            .showUnderneathOf(projectsButton)
    }

    fun playDeployment(projectId: String, deploymentId: String) {
        val token = auth.getAccessTokenOrNull() ?: run {
            console.print("ERROR: Not authenticated\n", ConsoleViewContentType.ERROR_OUTPUT)
            return
        }

        val TEAM_ID = "team_LcHi09XU4dhnyxLXGyrg8XjN" // hackathon hardcode

        stopFlag.set(false)
        ApplicationManager.getApplication().invokeLater { setHealth("Streaming…", kind = "loading") }

        console.print("INFO: Streaming runtime logs for deploy=$deploymentId\n", ConsoleViewContentType.SYSTEM_OUTPUT)

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                logsService.streamRuntimeLogs(
                    token = token,
                    projectId = projectId,
                    deploymentId = deploymentId,
                    teamId = TEAM_ID,
                    shouldStop = { stopFlag.get() },
                    onEvent = { e ->
                        val type =
                            if (e.isError) ConsoleViewContentType.ERROR_OUTPUT
                            else ConsoleViewContentType.NORMAL_OUTPUT

                        ApplicationManager.getApplication().invokeLater {
                            if (e.isError) setHealth("Error", kind = "error")
                            console.print(e.asConsoleLine(), type)
                        }
                    }
                )

                ApplicationManager.getApplication().invokeLater {
                    setHealth(
                        if (stopFlag.get()) "Stopped" else "Idle",
                        kind = if (stopFlag.get()) "stopped" else "idle"
                    )
                    console.print("INFO: Runtime log stream finished\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                }
            } catch (t: Throwable) {
                ApplicationManager.getApplication().invokeLater {
                    setHealth("Error", kind = "error")
                    console.print("ERROR: Runtime logs failed: ${t.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
                }
            }
        }
    }

    private fun stopPlayback() {
        stopFlag.set(true)
        console.print("INFO: Playback stopped\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        setHealth("Stopped", kind = "stopped")
    }

    private fun setHealth(text: String, kind: String) {
        when (kind) {
            "error" -> {
                healthLabel.text = "Health: ❌ $text"
                healthLabel.foreground = JBColor(0xC62828, 0xFF6B6B)
            }
            "stopped" -> {
                healthLabel.text = "Health: ⏹ $text"
                healthLabel.foreground = JBColor(0xF57F17, 0xFFD54F)
            }
            "loading" -> {
                healthLabel.text = "Health: ● $text"
                healthLabel.foreground = JBColor(0xF57F17, 0xFFD54F)
            }
            else -> {
                healthLabel.text = "Health: ● $text"
                healthLabel.foreground = JBColor(0x2E7D32, 0x7CFC00)
            }
        }
    }

    /* ---------------------- AI balloon helpers (from demo) ---------------------- */

    private fun showAiBalloonAtEditor(editor: Editor, line0: Int, col0: Int, text: String) {
        val doc = editor.document
        val safeLine = line0.coerceIn(0, doc.lineCount - 1)

        val lineStart = doc.getLineStartOffset(safeLine)
        val lineEnd = doc.getLineEndOffset(safeLine)
        val offset = (lineStart + col0).coerceIn(lineStart, lineEnd)

        editor.caretModel.moveToOffset(offset)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)

        val content = JBPanel<JBPanel<*>>(BorderLayout(8, 8)).apply {
            border = JBUI.Borders.empty(10)
            add(JBLabel("<html><b>AI suggestion</b></html>"), BorderLayout.NORTH)
            add(JBTextArea(text).apply {
                lineWrap = true
                wrapStyleWord = true
                isEditable = false
                columns = 34
                rows = 5
            }, BorderLayout.CENTER)
        }

        val balloon = JBPopupFactory.getInstance()
            .createBalloonBuilder(content)
            .setHideOnClickOutside(true)
            .setHideOnKeyOutside(true)
            .setCloseButtonEnabled(true)
            .createBalloon()

        val xy = editor.offsetToXY(offset)
        val visible = editor.scrollingModel.visibleArea
        val caretY = xy.y - visible.y
        val pos = if (caretY < visible.height / 3) Balloon.Position.below else Balloon.Position.above

        balloon.show(RelativePoint(editor.contentComponent, xy), pos)
    }

    private fun showAiBalloonInToolWindow(text: String) {
        val content = JBPanel<JBPanel<*>>(BorderLayout(8, 8)).apply {
            border = JBUI.Borders.empty(10)
            add(JBLabel("<html><b>AI suggestion</b></html>"), BorderLayout.NORTH)
            add(JBTextArea(text).apply {
                lineWrap = true
                wrapStyleWord = true
                isEditable = false
                columns = 36
                rows = 6
            }, BorderLayout.CENTER)
        }

        val balloon = JBPopupFactory.getInstance()
            .createBalloonBuilder(content)
            .setHideOnClickOutside(true)
            .setHideOnKeyOutside(true)
            .setCloseButtonEnabled(true)
            .createBalloon()

        balloon.show(RelativePoint.getSouthWestOf(consoleComponent), Balloon.Position.above)
    }

    private fun loadDeploymentsAndShowPopup(projectObj: VercelProject) {
        val token = auth.getAccessTokenOrNull() ?: return
        val projectId = projectObj.id ?: return

        setHealth("Loading deployments…", kind = "loading")

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val deps = deploymentsService.listDeployments(token, projectId)
                ApplicationManager.getApplication().invokeLater {
                    setHealth("Pick deployment", kind = "idle")
                    showDeploymentsPopup(projectObj, deps)
                }
            } catch (t: Throwable) {
                ApplicationManager.getApplication().invokeLater {
                    console.print("ERROR: Failed to load deployments: ${t.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
                    setHealth("Error", kind = "error")
                }
            }
        }
    }
    private val TEAM_ID = "team_LcHi09XU4dhnyxLXGyrg8XjN"
    private fun snapshotBuildLogs() {
        val token = auth.getAccessTokenOrNull() ?: return
        val depId = selectedDeploymentId ?: run {
            console.print("INFO: Select a deployment first\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            return
        }

        val TEAM_ID = "team_LcHi09XU4dhnyxLXGyrg8XjN"

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val events = logsService.getBuildLogsSnapshot(
                    token = token,
                    deploymentIdOrUrl = depId,
                    limit = 200,
                    teamId = TEAM_ID
                )

                ApplicationManager.getApplication().invokeLater {
                    console.print("INFO: Build logs snapshot (${events.size} lines)\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                    events.asReversed().forEach { e ->
                        val type =
                            if (e.isError) ConsoleViewContentType.ERROR_OUTPUT
                            else ConsoleViewContentType.NORMAL_OUTPUT
                        console.print(e.asConsoleLine(), type)
                    }
                }
            } catch (t: Throwable) {
                ApplicationManager.getApplication().invokeLater {
                    console.print("ERROR: Snapshot failed: ${t.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
                }
            }
        }
    }

    private fun redeployLikeFollowBuildLogs() {
        val token = auth.getAccessTokenOrNull() ?: return
        val depId = selectedDeploymentId ?: run {
            console.print("INFO: Select a deployment first\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            return
        }

        stopFlag.set(false)
        setHealth("Build logs (follow)…", "loading")

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                logsService.streamBuildLogs(
                    token = token,
                    deploymentIdOrUrl = depId,
                    teamId = TEAM_ID,
                    shouldStop = { stopFlag.get() },
                    onEvent = { e ->
                        val type =
                            if (e.isError) ConsoleViewContentType.ERROR_OUTPUT
                            else ConsoleViewContentType.NORMAL_OUTPUT

                        ApplicationManager.getApplication().invokeLater {
                            console.print(e.asConsoleLine(), type)
                        }
                    }
                )

                ApplicationManager.getApplication().invokeLater {
                    setHealth(if (stopFlag.get()) "Stopped" else "Idle", if (stopFlag.get()) "stopped" else "idle")
                }
            } catch (t: Throwable) {
                ApplicationManager.getApplication().invokeLater {
                    setHealth("Error", "error")
                    console.print("ERROR: Build follow failed: ${t.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
                }
            }
        }
    }



}


