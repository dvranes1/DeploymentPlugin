package com.github.dvranes1.deploymentplugin.UI

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.components.*
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.Timer


class DeployToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DeployToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)

        // DEMO: simuliraj logove da odmah vidiš UI “radi”
        panel.startDemoLogs()
    }
}

/** UI panel (layout kao na tvojoj slici) */
private class DeployToolWindowPanel(private val project: Project) : JBPanel<DeployToolWindowPanel>(BorderLayout()) {

    // TOP: “logovi u real-time”
    private val console = ConsoleViewImpl(project, /* viewer */ false)
    private val consoleComponent = console.component

    // MIDDLE: branch/commit/health
    private val branchLabel = JBLabel("Branch: main")
    private val commitLabel = JBLabel("Commit: abc1234")
    private val healthLabel = JBLabel("Health: ✅ Healthy")

    // BOTTOM: redeploy / stop
    private val redeployButton = JButton("Redeploy…")
    private val stopButton = JButton("Stop")

    init {
        val aiClient: AiClient = DemoAiClient()
        console.addMessageFilter(
            DeployErrorHyperlinkFilter(
                project = project,
                aiClient = DemoAiClient(),
                onOpenLocation = { _, _, _ ->
                    // opcionalno: možeš logovati da je kliknuto, ili update UI
                },
                onAiSuggestionReady = { file, line0, col0, suggestion ->
                    if(file != null) {
                        ApplicationManager.getApplication().invokeLater {
                            val descriptor = OpenFileDescriptor(project, file, line0, col0)

                            val editor = FileEditorManager.getInstance(project)
                                .openTextEditor(descriptor, true)
                                ?: return@invokeLater

                            showAiBalloonAtEditor(editor, line0, col0, suggestion)
                        }
                    }else{
                        showAiBalloonInToolWindow(suggestion)
                    }
                }
            )
        )

        val top = JBScrollPane(consoleComponent)

        val middle = JPanel(FlowLayout(FlowLayout.LEFT, 12, 6)).apply {
            add(branchLabel)
            add(commitLabel)
            add(healthLabel)
        }

        val bottom = JPanel(FlowLayout(FlowLayout.LEFT, 12, 6)).apply {
            add(redeployButton)
            add(stopButton)
        }

        add(top, BorderLayout.CENTER)
        add(middle, BorderLayout.NORTH)
        add(bottom, BorderLayout.SOUTH)

        redeployButton.addActionListener {
            RedeployDialog(project).show()
        }

        stopButton.addActionListener {
            console.print("INFO: Deploy stopped by user\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            healthLabel.text = "Health: ⏹️ Stopped"
        }
    }

    fun startDemoLogs() {
        console.print("INFO: Starting deploy...\n", ConsoleViewContentType.NORMAL_OUTPUT)

        var i = 0
        Timer(900) {
            i++

            if (i == 4) {
                // Zameni putanju realnom sa tvog projekta kad budeš testirao (ili ostavi za demo)
                console.print(
                    "Deploy failed: Unauthorized (missing token)\n",
                    ConsoleViewContentType.ERROR_OUTPUT
                )
                healthLabel.text = "Health: ❌ Error"
            } else {
                console.print("LOG: step=$i finished\n", ConsoleViewContentType.NORMAL_OUTPUT)
            }

            if (i >= 8) {
                console.print("INFO: Deploy done.\n", ConsoleViewContentType.NORMAL_OUTPUT)
                healthLabel.text = "Health: ✅ Healthy"
                (it.source as Timer).stop()
            }
        }.start()
    }

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

        // anchor: console komponenta u toolwindow-u
        balloon.show(RelativePoint.getSouthWestOf(consoleComponent), Balloon.Position.above)
    }






}
