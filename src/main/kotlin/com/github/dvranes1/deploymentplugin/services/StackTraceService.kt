package com.github.dvranes1.deploymentplugin.services

import com.github.dvranes1.deploymentplugin.errors.StackFrame
import com.github.dvranes1.deploymentplugin.toolwindow.SuggestionUI
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
        val top = frames.first()
        println(top)
        val prompt = PromptBuilder.build(project, top, frames)
        println(prompt)
        OpenAIClient.send(prompt) { response ->
            SuggestionUI.showSuggestion(project, response)
        }
    }
}