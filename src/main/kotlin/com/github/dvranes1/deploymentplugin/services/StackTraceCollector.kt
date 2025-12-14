package com.github.dvranes1.deploymentplugin.services

import com.github.dvranes1.deploymentplugin.UI.StackFrame

class StacktraceCollector(
        private val onComplete: (List<StackFrame>) -> Unit
) {
    private val buffer = mutableListOf<StackFrame>()
    private var collecting = false

    fun addFrame(frame: StackFrame) {
        collecting = true
        buffer.add(frame)
    }

    fun endTrace() {
        if (collecting && buffer.isNotEmpty()) {
            onComplete(buffer.toList())
        }
        buffer.clear()
        collecting = false
    }
}
