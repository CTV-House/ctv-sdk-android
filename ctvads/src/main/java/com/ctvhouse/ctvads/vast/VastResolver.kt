package com.ctvhouse.ctvads.vast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Resolves a VAST document into a playable [VastAd], following
 * `VASTAdTagURI` wrapper redirects up to [maxDepth] levels and merging the
 * tracking of every wrapper with the final inline ad (per the VAST spec).
 */
class VastResolver(
    private val http: OkHttpClient = defaultClient(),
    private val maxDepth: Int = 5,
) {

    suspend fun resolve(rootXml: String): VastAd? = withContext(Dispatchers.IO) {
        // Accumulated wrapper-level tracking, merged onto the inline ad.
        val impressions = mutableListOf<String>()
        val tracking = mutableMapOf<String, MutableList<String>>()
        val clickTracking = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var nroa: VastAd.NroaInform? = null

        var xml: String? = rootXml
        var depth = 0

        while (xml != null && depth <= maxDepth) {
            val parsed = VastParser.parse(xml) ?: return@withContext null

            impressions += parsed.impressions
            clickTracking += parsed.clickTracking
            errors += parsed.errors
            parsed.trackingEvents.forEach { (event, urls) ->
                tracking.getOrPut(event) { mutableListOf() }.addAll(urls)
            }
            // The inline ad's marking wins; otherwise keep a wrapper-level one.
            parsed.nroa?.let { nroa = it }

            val tagUri = parsed.wrapperTagUri
            if (tagUri == null) {
                // Inline ad reached — this document carries the actual creative.
                if (parsed.mediaFileUrl == null) return@withContext null
                return@withContext VastAd(
                    mediaFileUrl = parsed.mediaFileUrl,
                    impressions = impressions,
                    trackingEvents = tracking,
                    clickThrough = parsed.clickThrough,
                    clickTracking = clickTracking,
                    errors = errors,
                    durationMs = parsed.durationMs,
                    nroa = nroa,
                )
            }

            if (!parsed.followAdditionalWrappers) return@withContext null
            xml = fetch(tagUri)
            depth++
        }
        null
    }

    private fun fetch(url: String): String? = try {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}
