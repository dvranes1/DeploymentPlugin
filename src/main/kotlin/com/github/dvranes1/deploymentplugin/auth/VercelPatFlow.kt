package com.github.dvranes1.deploymentplugin.auth

import com.intellij.openapi.ui.Messages

class VercelPatFlow {

    fun loginBlocking(): TokenSet {
        val token = Messages.showInputDialog(
            null,
            "Paste your Vercel Personal Access Token (PAT):",
            "Vercel Login (PAT)",
            Messages.getQuestionIcon()
        )?.trim()

        if (token.isNullOrBlank()) error("No PAT provided")
        if (token.length < 20) error("PAT looks too short")

        return TokenSet(
            accessToken = token,
            refreshToken = null,
            expiresAtEpochSec = null
        )
    }
}
