package com.pigeonhub.app.push.installation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * MVP-007: connector pairing client. The phone issues short-lived one-time
 * codes with its management secret; the local connector redeems them.
 */
object PairingApi {

    data class IssuedCode(val code: String, val expiresAt: String)

    suspend fun issueCode(): IssuedCode? = withContext(Dispatchers.IO) {
        val state = InstallationRepository.state.value
        val credentials = InstallationRepository.currentCredentialsForApi() ?: return@withContext null
        val response = WorkerApi.request(
            method = "POST",
            url = "${InstallationRepository.workerOrigin()}/v1/installations/me/pairing-codes",
            bearer = credentials.managementSecret,
        )
        val json = runCatching { JSONObject(response.body) }.getOrNull() ?: return@withContext null
        if (response.code !in 200..299 || !json.optBoolean("ok")) return@withContext null
        val code = json.optStringOrNull("code") ?: return@withContext null
        IssuedCode(code = code, expiresAt = json.optStringOrNull("expires_at") ?: "")
    }

    suspend fun revokeCodes(): Boolean = withContext(Dispatchers.IO) {
        val credentials = InstallationRepository.currentCredentialsForApi() ?: return@withContext false
        val response = WorkerApi.request(
            method = "DELETE",
            url = "${InstallationRepository.workerOrigin()}/v1/installations/me/pairing-codes",
            bearer = credentials.managementSecret,
        )
        response.code in 200..299
    }
}
