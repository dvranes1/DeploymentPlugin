package com.github.dvranes1.deploymentplugin.vercel

import VercelApiClient

class VercelDeploymentsService(private val api: VercelApiClient) {

    fun listDeployments(token: String, projectId: String): List<VercelDeployment> {
        val url = "https://api.vercel.com/v6/deployments?projectId=$projectId&limit=20"
        val json = api.getJson(url, token)

        val arr = json.getAsJsonArray("deployments") ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.asJsonObject

            val meta = o.getAsJsonObject("meta")
            val branch = meta?.get("githubCommitRef")?.asString
            val sha = meta?.get("githubCommitSha")?.asString

            VercelDeployment(
                id = o.get("uid")?.asString ?: o.get("id")?.asString,
                state = o.get("state")?.asString,
                createdAt = o.get("createdAt")?.asLong,
                branch = branch,
                sha = sha
            )
        }
    }

    fun getLatestDeployment(token: String, projectId: String): VercelDeployment? {
        return listDeployments(token, projectId).firstOrNull()
    }

    fun getDeployment(token: String, deploymentId: String): VercelDeployment? {

        return null
    }
}
