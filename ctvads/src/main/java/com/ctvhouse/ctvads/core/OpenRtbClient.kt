package com.ctvhouse.ctvads.core

import android.content.Context
import com.ctvhouse.ctvads.AdError
import com.ctvhouse.ctvads.AdSize
import com.ctvhouse.ctvads.CtvAds
import com.ctvhouse.ctvads.openrtb.Bid
import com.ctvhouse.ctvads.openrtb.BidRequest
import com.ctvhouse.ctvads.openrtb.BidResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Direct OpenRTB 2.5 client. Sends a [BidRequest] to the configured bidder
 * endpoint and returns the highest-priced [Bid].
 */
class OpenRtbClient(context: Context) {

    private val appContext = context.applicationContext
    private val config = CtvAds.config()
    private val factory = BidRequestFactory(appContext, config)

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val http = OkHttpClient.Builder()
        .callTimeout(config.timeoutMs + 500, TimeUnit.MILLISECONDS)
        .connectTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    suspend fun requestBanner(
        size: AdSize,
        bidFloor: Double? = null,
        placementId: String? = null,
    ): Result<Bid> = auction { factory.banner(size, bidFloor, placementId) }

    suspend fun requestVideo(
        size: AdSize,
        bidFloor: Double? = null,
        placementId: String? = null,
    ): Result<Bid> = auction { factory.video(size, bidFloor, placementId) }

    // The builder runs on Dispatchers.IO because DeviceInfo.build() performs a
    // blocking GAID lookup that must not happen on the main thread.
    private suspend fun auction(buildRequest: () -> BidRequest): Result<Bid> = withContext(Dispatchers.IO) {
        // Bidder disabled via /status — don't hit the auction endpoint.
        if (!CtvAds.canRequestAds()) return@withContext Result.failure(AdError.Stopped.toEx())
        try {
            val bidRequest = buildRequest()
            val payload = json.encodeToString(BidRequest.serializer(), bidRequest)
            val request = Request.Builder()
                .url(config.endpointUrl)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("x-openrtb-version", "2.5")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = http.newCall(request).await()
            response.use {
                if (response.code == 204) return@withContext Result.failure(AdError.NoBid.toEx())
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        AdError.InvalidResponse("HTTP ${response.code}").toEx()
                    )
                }
                val body = response.body?.string()
                    ?: return@withContext Result.failure(AdError.NoBid.toEx())

                val decoded = json.decodeFromString(BidResponse.serializer(), body)
                val bid = decoded.seatbid
                    ?.flatMap { it.bid }
                    ?.maxByOrNull { it.price }
                    ?: return@withContext Result.failure(AdError.NoBid.toEx())

                // Win notice (nurl): fired as soon as the SDK receives the bid.
                fireNotice(bid.nurl)
                Result.success(bid)
            }
        } catch (e: IOException) {
            Result.failure(AdError.Network(e).toEx())
        } catch (e: Exception) {
            Result.failure(AdError.InvalidResponse(e.message ?: "parse error").toEx())
        }
    }

    /** Fires a win/billing/loss notice URL (fire-and-forget). */
    fun fireNotice(url: String?) {
        if (url.isNullOrBlank()) return
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** Bridges [AdError] into a [Throwable] for use in [Result]. */
internal fun AdError.toEx(): Throwable = AdException(this)

class AdException(val error: AdError) : Exception(error.message, error.cause)

/** Suspends until the OkHttp [Call] completes. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation {
        try {
            cancel()
        } catch (_: Throwable) {
        }
    }
}
