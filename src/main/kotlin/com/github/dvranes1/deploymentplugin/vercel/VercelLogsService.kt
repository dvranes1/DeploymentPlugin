package com.github.dvranes1.deploymentplugin.vercel

import VercelApiClient
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class VercelLogsService(private val api: VercelApiClient) {

    fun getBuildLogsSnapshot(
        token: String,
        deploymentIdOrUrl: String,
        limit: Int = 200,
        teamId: String? = null,
        slug: String? = null
    ): List<VercelLogEvent> {
        val url = buildString {
            append("https://api.vercel.com/v3/deployments/")
            append(encPath(deploymentIdOrUrl))
            append("/events")

            val qp = mutableListOf<String>()
            qp += "builds=1"
            qp += "delimiter=1"
            qp += "direction=backward"
            qp += "follow=0"
            qp += "limit=$limit"
            if (!teamId.isNullOrBlank()) qp += "teamId=${enc(teamId)}"
            if (!slug.isNullOrBlank()) qp += "slug=${enc(slug)}"

            append("?").append(qp.joinToString("&"))
        }

        val body = api.getString(url, token)
        val arr = JsonParser.parseString(body).asJsonArray
        return arr.mapNotNull { parseDeploymentEventToLog(it) }
    }

    fun streamBuildLogs(
        token: String,
        deploymentIdOrUrl: String,
        teamId: String? = null,
        slug: String? = null,
        shouldStop: () -> Boolean = { false },
        onEvent: (VercelLogEvent) -> Unit
    ) {
        val url = buildString {
            append("https://api.vercel.com/v3/deployments/")
            append(encPath(deploymentIdOrUrl))
            append("/events")

            val qp = mutableListOf<String>()
            qp += "builds=1"
            qp += "delimiter=1"
            qp += "direction=forward"
            qp += "follow=1"
            qp += "limit=-1"
            if (!teamId.isNullOrBlank()) qp += "teamId=${enc(teamId)}"
            if (!slug.isNullOrBlank()) qp += "slug=${enc(slug)}"

            append("?").append(qp.joinToString("&"))
        }

        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .GET()
            .build()

        val res = api.httpClient().send(req, HttpResponse.BodyHandlers.ofInputStream())
        if (res.statusCode() !in 200..299) {
            val err = res.body().readBytes().toString(StandardCharsets.UTF_8)
            throw RuntimeException("HTTP ${res.statusCode()} body=${err.take(500)}")
        }

        streamJsonObjects(res.body(), shouldStop) { obj ->
            val ev = parseDeploymentEventToLog(obj)
            if (ev != null) onEvent(ev)
        }
    }

    fun streamRuntimeLogs(
        token: String,
        projectId: String,
        deploymentId: String,
        teamId: String? = null,
        slug: String? = null,
        shouldStop: () -> Boolean = { false },
        onEvent: (VercelLogEvent) -> Unit
    ) {
        val url = buildString {
            append("https://api.vercel.com/v1/projects/")
            append(encPath(projectId))
            append("/deployments/")
            append(encPath(deploymentId))
            append("/runtime-logs")

            val qp = mutableListOf<String>()
            if (!teamId.isNullOrBlank()) qp += "teamId=${enc(teamId)}"
            if (!slug.isNullOrBlank()) qp += "slug=${enc(slug)}"
            if (qp.isNotEmpty()) append("?").append(qp.joinToString("&"))
        }

        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .GET()
            .build()

        val res = api.httpClient().send(req, HttpResponse.BodyHandlers.ofInputStream())
        if (res.statusCode() !in 200..299) {
            val body = res.body().readBytes().toString(StandardCharsets.UTF_8)
            throw RuntimeException("HTTP ${res.statusCode()} body=${body.take(500)}")
        }

        BufferedReader(InputStreamReader(res.body(), StandardCharsets.UTF_8)).use { br ->
            while (!shouldStop()) {
                val line = br.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                try {
                    val o = JsonParser.parseString(trimmed).asJsonObject

                    val msg = o.get("message")?.asString
                        ?: o.get("text")?.asString
                        ?: trimmed

                    val level = o.get("level")?.asString
                        ?: o.get("type")?.asString

                    onEvent(VercelLogEvent(message = msg, level = level))
                } catch (_: Throwable) {
                    onEvent(VercelLogEvent(message = trimmed, level = "info"))
                }
            }
        }
    }


    private fun parseDeploymentEventToLog(el: JsonElement): VercelLogEvent? {
        val obj = el.asJsonObject
        val type = obj.get("type")?.asString ?: "info"
        val payload = obj.getAsJsonObject("payload")

        val msg =
            payload?.get("text")?.asString
                ?: payload?.get("message")?.asString
                ?: payload?.get("data")?.asJsonObject?.get("text")?.asString
                ?: payload?.get("data")?.asJsonObject?.get("message")?.asString
                ?: obj.get("text")?.asString
                ?: return null

        val level = when (type.lowercase()) {
            "stderr", "fatal", "error" -> "error"
            else -> "info"
        }
        return VercelLogEvent(message = msg, level = level)
    }


    private fun streamJsonObjects(
        input: InputStream,
        shouldStop: () -> Boolean,
        onJson: (JsonElement) -> Unit
    ) {
        BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { br ->
            while (!shouldStop()) {
                val line = br.readLine() ?: break
                val t = line.trim()
                if (t.isEmpty()) continue
                try {
                    onJson(JsonParser.parseString(t))
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun encPath(s: String) = s
}
