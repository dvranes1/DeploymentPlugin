package com.github.dvranes1.deploymentplugin.services

import com.intellij.util.concurrency.AppExecutorUtil
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.swing.SwingUtilities

object OpenAIClient {

    private val client = OkHttpClient()

    val openAiKey = "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"
    fun send(prompt: String, onResult: (String) -> Unit) {

        AppExecutorUtil.getAppExecutorService().submit {
            try {
                val payload = JSONObject().apply {
                    put("model", "gpt-5.2")
                    put("messages", listOf(
                        JSONObject().put("role", "user").put("content", prompt)
                    ))
                }

                val request = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .header("Authorization", "Bearer $openAiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val raw = response.body?.string().orEmpty()
                println(raw)

                val result = JSONObject(raw)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")


                SwingUtilities.invokeLater {
                    onResult(result)
                }

            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    onResult("Error: ${e.message}")
                }
            }
        }
    }
}