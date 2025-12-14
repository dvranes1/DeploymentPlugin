package com.github.dvranes1.deploymentplugin.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference


@Service(Service.Level.PROJECT)
class VercelAuthService {

    private val store = TokenStore()

    @Volatile private var cached: TokenSet? = null

    fun isAuthenticated(): Boolean = getAccessTokenOrNull() != null

    fun logout() {
        cached = null
        store.clear()
    }

    fun startOAuthLoginAsync(
        project: Project,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        AppExecutorUtil.getAppExecutorService().submit {
            try {
                val ref = AtomicReference<TokenSet?>()
                val err = AtomicReference<Throwable?>()

                ApplicationManager.getApplication().invokeAndWait {
                    try {
                        ref.set(VercelPatFlow().loginBlocking())
                    } catch (t: Throwable) {
                        err.set(t)
                    }
                }

                err.get()?.let { throw it }
                val tokenSet = ref.get() ?: error("No token returned")

                saveTokenSet(tokenSet)
                val t = getAccessTokenOrNull()
                thisLogger().warn("PAT login success, tokenPresent=${t != null}")

                ApplicationManager.getApplication().invokeLater(onSuccess)
            } catch (t: Throwable) {
                thisLogger().warn("PAT login failed", t)
                ApplicationManager.getApplication().invokeLater { onError(t) }
            }
        }
    }

    fun getAccessTokenOrNull(): String? {
        val ts = loadTokenSet() ?: return null
        val now = Instant.now().epochSecond

        if (!ts.isExpired(now)) return ts.accessToken
        return null
    }

    private fun loadTokenSet(): TokenSet? {
        cached?.let { return it }

        val raw = store.load() ?: return null
        val json = JsonParser.parseString(raw).asJsonObject

        val access = json["accessToken"]?.asString ?: return null
        val refresh = json.get("refreshToken")?.asString
        val expiresAt = json.get("expiresAtEpochSec")?.asLong

        return TokenSet(access, refresh, expiresAt).also { cached = it }
    }

    private fun saveTokenSet(ts: TokenSet) {
        cached = ts

        val json = JsonObject().apply {
            addProperty("accessToken", ts.accessToken)
            addProperty("refreshToken", ts.refreshToken)
            addProperty("expiresAtEpochSec", ts.expiresAtEpochSec)
        }
        store.save(json.toString())
    }
}
