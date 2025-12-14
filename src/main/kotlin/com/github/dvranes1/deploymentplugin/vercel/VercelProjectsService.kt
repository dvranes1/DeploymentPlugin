package com.github.dvranes1.deploymentplugin.vercel

import VercelApiClient
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonParser

class VercelProjectsService(private val api: VercelApiClient) {

    fun listProjects(token: String): List<VercelProject> {
        val url = buildString {
            append("https://api.vercel.com/v10/projects")

        }

        val json: JsonObject = api.getJson(url, token)

        val arr: JsonArray = json.get("projects")?.asJsonArray ?: return emptyList()

        return arr.map { el ->
            val o = el.asJsonObject
            VercelProject(
                id = o.get("id").asString,
                name = o.get("name").asString
            )
        }
    }

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

data class ProjectDto(val id: String, val name: String)



