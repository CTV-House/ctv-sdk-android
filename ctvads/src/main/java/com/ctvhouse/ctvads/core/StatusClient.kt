package com.ctvhouse.ctvads.core

import com.ctvhouse.ctvads.InitResult
import com.ctvhouse.ctvads.SdkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Calls the bidder technical `/status` endpoint and parses
 * `{"status":"active|stopped","version":"…"}`.
 */
internal object StatusClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class StatusResponse(
        val status: String? = null,
        val version: String? = null,
    )

    suspend fun check(statusUrl: String): InitResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(statusUrl).get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext InitResult(
                        status = SdkStatus.UNKNOWN,
                        error = IllegalStateException("HTTP ${response.code}"),
                    )
                }
                val body = response.body?.string()
                    ?: return@withContext InitResult(
                        status = SdkStatus.UNKNOWN,
                        error = IllegalStateException("empty status body"),
                    )
                val parsed = json.decodeFromString(StatusResponse.serializer(), body)
                val status = when (parsed.status?.trim()?.lowercase()) {
                    "active" -> SdkStatus.ACTIVE
                    "stopped" -> SdkStatus.STOPPED
                    else -> SdkStatus.UNKNOWN
                }
                InitResult(status = status, version = parsed.version)
            }
        } catch (t: Throwable) {
            InitResult(status = SdkStatus.UNKNOWN, error = t)
        }
    }
}
