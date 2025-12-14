package com.github.dvranes1.deploymentplugin.auth

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.credentialStore.Credentials

class TokenStore {
    private val key = CredentialAttributes("com.github.dvranes1.deploymentplugin.vercel")

    fun load(): String? {
        val creds = PasswordSafe.instance.get(key) ?: return null
        return creds.password?.toString()
    }

    fun save(rawJson: String) {
        PasswordSafe.instance.set(key, Credentials("vercel", rawJson))
    }

    fun clear() {
        PasswordSafe.instance.set(key, null)
    }

}
