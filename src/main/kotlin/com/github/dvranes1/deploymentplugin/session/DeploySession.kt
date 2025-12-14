package com.github.dvranes1.deploymentplugin.session

import com.github.dvranes1.deploymentplugin.auth.VercelAuthService
import com.github.dvranes1.deploymentplugin.vercel.VercelDeploymentsService
import com.github.dvranes1.deploymentplugin.vercel.VercelLogsService
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class DeploySession(
    private val project: Project,
    private val console: ConsoleViewImpl,
    private val auth: VercelAuthService,
    private val deploymentsService: VercelDeploymentsService,
    private val logsService: VercelLogsService,
    private val onMeta: (branch: String?, sha: String?, state: String?) -> Unit
) {
    private val stopFlag = AtomicBoolean(false)

    fun stopPlayback() {
        stopFlag.set(true)
        console.print("INFO: Playback stopped\n", ConsoleViewContentType.SYSTEM_OUTPUT)
    }

    fun playLatestDeployment(projectId: String) {
        val token = auth.getAccessTokenOrNull() ?: run {
            console.print("ERROR: Not authenticated\n", ConsoleViewContentType.ERROR_OUTPUT)
            return
        }

        stopFlag.set(false)

        ApplicationManager.getApplication().executeOnPooledThread {
            val latest = deploymentsService.getLatestDeployment(token, projectId) ?: return@executeOnPooledThread
            playDeployment(projectId, latest.id!!)
        }
    }

    fun playDeployment(projectId: String, deploymentId: String) {
        val token = auth.getAccessTokenOrNull() ?: run {
            console.print("ERROR: Not authenticated\n", ConsoleViewContentType.ERROR_OUTPUT)
            return
        }

        stopFlag.set(false)

        console.print(
            "INFO: Streaming runtime logs for deploy=$deploymentId\n",
            ConsoleViewContentType.SYSTEM_OUTPUT
        )

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                logsService.streamRuntimeLogs(
                    token = token,
                    projectId = projectId,
                    deploymentId = deploymentId,
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
                    console.print(
                        "INFO: Runtime log stream finished\n",
                        ConsoleViewContentType.SYSTEM_OUTPUT
                    )
                }
            } catch (t: Throwable) {
                ApplicationManager.getApplication().invokeLater {
                    console.print(
                        "ERROR: Runtime logs failed: ${t.message}\n",
                        ConsoleViewContentType.ERROR_OUTPUT
                    )
                }
            }
        }
    }

}
