package com.github.dvranes1.deploymentplugin.auth

import java.time.Instant

data class TokenSet(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtEpochSec: Long? = null
) {
    fun isExpired(nowEpochSeconds: Long = Instant.now().epochSecond): Boolean {
        val exp = expiresAtEpochSec ?: return false
        return nowEpochSeconds >= exp
    }
}
