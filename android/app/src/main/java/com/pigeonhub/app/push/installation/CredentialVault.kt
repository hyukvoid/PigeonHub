package com.pigeonhub.app.push.installation

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * MVP-001C local credential storage primitives.
 *
 * Secrets are generated on-device BEFORE any network request and persisted
 * (Android Keystore AES-GCM key + encrypted blob in DataStore) so bootstrap can
 * resume after process death without minting new credentials.
 *
 * If the Keystore key is lost (device migration, factory reset), decryption
 * fails and the state becomes RECOVERY_REQUIRED — that is never silently
 * treated as a successful recovery and never regenerates over an installation.
 */
object CredentialVault {

    const val KEYSTORE_ALIAS = "pigeonhub_installation_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12

    data class Credentials(
        val bootstrapId: String,
        val managementSecret: String,
        val writeToken: String,
    )

    /** Returns null when decryption is impossible (Keystore key lost) — never regenerates. */
    fun decryptBlob(blob: String): Credentials? {
        return try {
            val data = Base64.decode(blob, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, obtainKeystoreKey(), GCMParameterSpec(128, data, 0, IV_LENGTH))
            val plain = cipher.doFinal(data, IV_LENGTH, data.size - IV_LENGTH)
            val json = org.json.JSONObject(String(plain, Charsets.UTF_8))
            Credentials(
                bootstrapId = json.getString("bootstrap_id"),
                managementSecret = json.getString("management_secret"),
                writeToken = json.getString("write_token"),
            )
        } catch (error: Exception) {
            android.util.Log.e("PigeonHub", "credential blob undecryptable: ${error.javaClass.simpleName}")
            null
        }
    }

    fun encryptBlob(credentials: Credentials): String {
        val json = org.json.JSONObject()
            .put("bootstrap_id", credentials.bootstrapId)
            .put("management_secret", credentials.managementSecret)
            .put("write_token", credentials.writeToken)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainKeystoreKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val blob = ByteArray(iv.size + encrypted.size)
        iv.copyInto(blob)
        encrypted.copyInto(blob, IV_LENGTH)
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    fun generateCredentials(): Credentials = Credentials(
        bootstrapId = "boot-" + UUID.randomUUID().toString().replace("-", ""),
        managementSecret = randomUrlSafe(32),
        writeToken = randomUrlSafe(32),
    )

    /** 256-bit cryptographically secure random, URL-safe, no padding. */
    fun randomUrlSafe(bytes: Int): String {
        val buf = ByteArray(bytes)
        SecureRandom().nextBytes(buf)
        return Base64.encodeToString(buf, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
            .trimEnd('=')
    }

    private fun obtainKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
