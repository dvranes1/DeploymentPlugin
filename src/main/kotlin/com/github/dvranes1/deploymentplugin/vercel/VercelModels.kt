package com.github.dvranes1.deploymentplugin.vercel


data class VercelProject(
    val id: String?,
    val name: String?
) {
    override fun toString(): String = name ?: id ?: "Unknown project"
}

data class VercelDeployment(
    val id: String?,
    val state: String?,
    val createdAt: Long?,
    val branch: String?,
    val sha: String?
) {
    override fun toString(): String {
        val short = (sha ?: "-").take(7)
        return "state=${state ?: "?"}  sha=$short  branch=${branch ?: "-"}  id=${id?.take(10) ?: "-"}"
    }
}

data class VercelLogEvent(
    val message: String,
    val level: String? = null,
    val file: String? = null,
    val line: Int? = null,
    val col: Int? = null,
) {
    val isError: Boolean get() = level?.equals("error", true) == true || message.contains("error", true)

    fun asConsoleLine(): String {
        return if (file != null && line != null && col != null) {
            "ERROR: $file:$line:$col - $message\n"
        } else {
            "$message\n"
        }
    }
}
