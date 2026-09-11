package com.pigeonhub.app.push.installation

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** Minimal JSON HTTP client for the Worker API (no extra dependencies). */
object WorkerApi {

    data class Response(val code: Int, val body: String)

    fun request(method: String, url: String, bearer: String?, bodyJson: String? = null): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            if (bearer !== null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            if (bodyJson !== null) {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            Response(connection.responseCode, text)
        } finally {
            connection.disconnect()
        }
    }
}

/** Serializes/deserializes the bootstrap + management request bodies. */
object ApiCodec {

    fun bootstrapBody(
        bootstrapId: String,
        inviteCode: String,
        writeTokenHash: String,
        fcmToken: String,
    ): String = JSONObject()
        .put("bootstrap_id", bootstrapId)
        .put("invite_code", inviteCode)
        .put("write_token_hash", writeTokenHash)
        .put("fcm_token", fcmToken)
        .put("platform", "android")
        .toString()

    fun pushTokenBody(fcmToken: String, expectedVersion: Int): String = JSONObject()
        .put("fcm_token", fcmToken)
        .put("expected_version", expectedVersion)
        .toString()

    fun rotationBody(writeTokenHash: String, expectedVersion: Int): String = JSONObject()
        .put("write_token_hash", writeTokenHash)
        .put("expected_version", expectedVersion)
        .toString()

    fun messageBody(title: String, message: String, priority: String): String = JSONObject()
        .put("title", title)
        .put("message", message)
        .put("priority", priority)
        .toString()
}
