package com.pigeonhub.app.push.installation

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * MVP-007: connector pairing client. The phone issues short-lived one-time
 * codes with its management secret; the local connector redeems them.
 */
object PairingApi {

    data class IssuedCode(val code: String, val expiresAt: String)

    /** Outcome of approving a scanned PC login QR. */
    data class ApprovalResult(val ok: Boolean, val expired: Boolean = false)

    /** Approve a PC-generated login QR after the user confirms the device. */
    suspend fun approveLoginRequest(requestId: String, challenge: String): ApprovalResult = withContext(Dispatchers.IO) {
        val credentials = InstallationRepository.currentCredentialsForApi()
            ?: return@withContext ApprovalResult(ok = false)
        val response = WorkerApi.request(
            method = "POST",
            url = "${InstallationRepository.workerOrigin()}/v1/pairing/requests/${Uri.encode(requestId)}/approve",
            bearer = credentials.managementSecret,
            bodyJson = JSONObject().put("challenge", challenge).toString(),
        )
        val json = runCatching { JSONObject(response.body) }.getOrNull()
        val ok = response.code in 200..299 && (json?.optBoolean("ok") == true)
        // The worker answers 410 once a login request has expired; a wrong or
        // already-consumed request comes back as 403 with an error detail.
        val error = json?.optStringOrNull("error").orEmpty()
        val expired = response.code == 410 || error.contains("expired", ignoreCase = true)
        ApprovalResult(ok = ok, expired = expired)
    }

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
