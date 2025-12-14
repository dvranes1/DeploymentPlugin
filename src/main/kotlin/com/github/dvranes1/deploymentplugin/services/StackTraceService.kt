package com.github.dvranes1.deploymentplugin.services


import com.github.dvranes1.deploymentplugin.UI.StackFrame
import com.intellij.openapi.project.Project


interface StacktraceListener {

    fun onStackFrame(frame: StackFrame)

    fun onNonFrameLine(line: String)
}

class StacktraceService(private val project: Project) : StacktraceListener {

    private val collector = StacktraceCollector { trace ->
        handleFullTrace(trace)
    }

    override fun onStackFrame(frame: StackFrame) {
        collector.addFrame(frame)
    }

    override fun onNonFrameLine(line: String) {
        collector.endTrace()
    }

    private fun handleFullTrace(frames: List<StackFrame>) {
        print("usao u full trace")
        val top = frames.first()

        val prompt = PromptBuilder.build(project, top, frames)

        OpenAIClient.send(prompt) { response ->
            SuggestionUI.showSuggestion(project, response)
        }
    }
}
