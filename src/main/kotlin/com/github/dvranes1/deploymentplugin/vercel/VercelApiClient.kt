import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class VercelApiClient {
    private val client = HttpClient.newBuilder().build()

    fun httpClient(): HttpClient = client

    fun getString(url: String, token: String): String {
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .GET()
            .build()

        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) {
            throw RuntimeException("HTTP ${res.statusCode()} body=${res.body().take(500)}")
        }
        return res.body()
    }

    fun getJson(url: String, token: String): JsonObject {
        val body = getString(url, token)
        return JsonParser.parseString(body).asJsonObject
    }
}
