package com.agentdeck.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small secrets with an AES-256-GCM key held by the Android Keystore.
 * Only ciphertext is written to SharedPreferences; the key never leaves the keystore.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("agentdeck.secrets", Context.MODE_PRIVATE)

    fun has(key: String): Boolean = prefs.contains(key)

    fun put(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        // Commit synchronously: callers report the key as saved right after this.
        prefs.edit().putString(key, payload).commit()
    }

    fun get(key: String): String? {
        val payload = prefs.getString(key, null) ?: return null
        val plain = runCatching {
            val separator = payload.indexOf(':')
            require(separator > 0)
            val iv = Base64.decode(payload.substring(0, separator), Base64.NO_WRAP)
            val data = Base64.decode(payload.substring(separator + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrNull()
        if (plain == null) prefs.edit().remove(key).apply()
        return plain
    }

    fun remove(key: String) {
        prefs.edit().remove(key).commit()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val CLOUD_API_KEY = "cloud.api-key"
        const val GITHUB_TOKEN = "github.token"
        const val HOST_TOKEN = "host.token"
        const val SSH_PASSWORD = "ssh.password"

        /** SecureStore key that holds a cloud profile's API key. */
        fun cloudKeyId(profileId: String) = "cloud.key.$profileId"

        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "agentdeck.secrets.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
