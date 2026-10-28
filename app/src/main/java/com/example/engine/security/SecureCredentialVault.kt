package com.example.engine.security

import android.content.Context
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/**
 * Encrypts small application credentials with an Android Keystore AES-GCM key. Only ciphertext
 * and the random IV are written to private SharedPreferences; the Keystore key is non-exportable.
 */
class SecureCredentialVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun read(key: String): String? {
        val encoded = preferences.getString(key, null) ?: return null
        return try {
            decrypt(encoded)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun write(key: String, value: String?): Boolean {
        if (value.isNullOrBlank()) {
            return preferences.edit().remove(key).commit()
        }
        return try {
            preferences.edit().putString(key, encrypt(value)).commit()
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun remove(key: String): Boolean = preferences.edit().remove(key).commit()

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = ByteArrayOutputStream(1 + iv.size + ciphertext.size).apply {
            write(iv.size)
            write(iv)
            write(ciphertext)
        }.toByteArray()
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.isNotEmpty()) { "Encrypted credential is empty" }
        val ivLength = packed[0].toInt() and 0xFF
        require(ivLength in MIN_IV_LENGTH..MAX_IV_LENGTH && packed.size > 1 + ivLength) {
            "Encrypted credential has an invalid format"
        }
        val iv = packed.copyOfRange(1, 1 + ivLength)
        val ciphertext = packed.copyOfRange(1 + ivLength, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val PREFERENCES_NAME = "mangaflow_translation_credentials"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "mangaflow.translation.api-key.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val MIN_IV_LENGTH = 12
        private const val MAX_IV_LENGTH = 16
    }
}
