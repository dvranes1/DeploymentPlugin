package com.github.dvranes1.deploymentplugin.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Pkce {
    private val rnd = SecureRandom()

    fun generateVerifier(): String {
        val bytes = ByteArray(32)
        rnd.nextBytes(bytes)
        return base64Url(bytes)
    }

    fun challengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashed = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64Url(hashed)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}