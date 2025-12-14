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
    val openAiKey = System.getenv("OPENAI_API_KEY")
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
                val text = response.body?.string() ?: "No response"

                val result = JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                // UI update u Swing thread
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
