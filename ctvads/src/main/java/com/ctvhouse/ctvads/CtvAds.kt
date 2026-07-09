package com.ctvhouse.ctvads

import android.content.Context
import com.ctvhouse.ctvads.core.StatusClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry point / global configuration for the CTV Ads SDK.
 *
 * Call [initialize] once (e.g. from your `Application`) before requesting ads.
 *
 * This SDK talks to an OpenRTB 2.5 bidder **directly** (no Prebid Server). You
 * configure only the bidder [CtvAdsConfig.host]; the SDK derives the request
 * endpoints from it:
 *
 * - `http(s)://host/ads` — OpenRTB auction ([CtvAdsConfig.endpointUrl]);
 * - `http(s)://host/status` — technical status check ([CtvAdsConfig.statusUrl]).
 *
 * On [initialize] the SDK pings `/status` and reads a JSON
 * `{"status":"active|stopped","version":"…"}`. If the bidder reports `stopped`,
 * ad requests are blocked ([canRequestAds] returns false).
 */
object CtvAds {

    @Volatile
    private var config: CtvAdsConfig? = null

    /** Bidder status from the last `/status` check. */
    @Volatile
    var status: SdkStatus = SdkStatus.UNKNOWN
        private set

    /** Bidder version reported by `/status`, if any. */
    @Volatile
    var bidderVersion: String? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Initializes the SDK and asynchronously checks the bidder `/status`
     * endpoint. [listener] (optional) is invoked on the main thread with the
     * result once the check completes.
     */
    @JvmStatic
    @JvmOverloads
    fun initialize(context: Context, config: CtvAdsConfig, listener: InitListener? = null) {
        val resolved = config.copy(
            appBundle = config.appBundle.ifBlank { context.packageName }
        )
        this.config = resolved
        status = SdkStatus.UNKNOWN
        bidderVersion = null
        scope.launch {
            val result = StatusClient.check(resolved.statusUrl)
            status = result.status
            bidderVersion = result.version
            withContext(Dispatchers.Main) { listener?.onInitialized(result) }
        }
    }

    fun config(): CtvAdsConfig =
        config ?: error("CtvAds is not initialized. Call CtvAds.initialize() first.")

    val isInitialized: Boolean get() = config != null

    /** False when the bidder reported `stopped` — ad requests must be blocked. */
    fun canRequestAds(): Boolean = status != SdkStatus.STOPPED
}

/** Result of the bidder `/status` check passed to [InitListener]. */
data class InitResult(
    val status: SdkStatus,
    val version: String? = null,
    /** Set when the status could not be determined (network/parse error). */
    val error: Throwable? = null,
)

/** Bidder operational status reported by the `/status` endpoint. */
enum class SdkStatus {
    /** Bidder is serving; ad requests are allowed. */
    ACTIVE,

    /** Bidder is disabled; ad requests are blocked by the SDK. */
    STOPPED,

    /** Not checked yet or the status endpoint was unreachable/invalid. */
    UNKNOWN,
}

/** Callback delivered on the main thread once [CtvAds.initialize] completes. */
fun interface InitListener {
    fun onInitialized(result: InitResult)
}

/**
 * Immutable SDK configuration.
 *
 * @param host         Bidder host, e.g. `bidder.example.com`, `10.0.2.2:8080` or
 *                     `https://bidder.example.com`. Scheme is optional (defaults
 *                     to `http://`). Request URLs are derived as `host/ads` and
 *                     `host/status`.
 * @param appName      Human-readable app name sent in `BidRequest.app.name`.
 * @param appBundle    App bundle/package. Defaults to the host package name.
 * @param appStoreUrl  Store URL of the app, if published.
 * @param appDomain    Publisher domain.
 * @param publisherId  Publisher ID as recognized by the bidder.
 * @param timeoutMs    Network + auction timeout in milliseconds.
 * @param test         When true, sets `BidRequest.test = 1` (no billing).
 */
data class CtvAdsConfig(
    val host: String,
    val appName: String,
    val appBundle: String = "",
    val appStoreUrl: String? = null,
    val appDomain: String? = null,
    val publisherId: String? = null,
    val timeoutMs: Long = 1500,
    val test: Boolean = false,
) {
    /** Normalized base URL (scheme guaranteed, no trailing slash). */
    val baseUrl: String
        get() {
            val trimmed = host.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "http://$trimmed"
            }
        }

    /** OpenRTB auction endpoint (`host/ads`). */
    val endpointUrl: String get() = "$baseUrl/ads"

    /** Technical status endpoint (`host/status`). */
    val statusUrl: String get() = "$baseUrl/status"
}
