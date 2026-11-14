package com.budget.app.data.secure

import com.budget.app.data.Settings

/**
 * The device token, encrypted with the Keystore key and kept in the app's
 * preferences; a decrypted copy is held in memory for the API client.
 */
class TokenStore(private val settings: Settings) {
    @Volatile
    private var cached: String? = null

    fun current(): String? = cached

    suspend fun load(): String? {
        cached = settings.get(KEY)?.let { TokenCrypto.decrypt(it) }
        return cached
    }

    suspend fun save(token: String) {
        settings.set(KEY, TokenCrypto.encrypt(token))
        cached = token
    }

    suspend fun clear() {
        settings.set(KEY, null)
        cached = null
    }

    private companion object {
        const val KEY = "device_token_enc"
    }
}
