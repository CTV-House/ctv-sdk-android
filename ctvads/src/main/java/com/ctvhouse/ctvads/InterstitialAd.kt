package com.ctvhouse.ctvads

import android.content.Context
import android.content.Intent
import com.ctvhouse.ctvads.core.AdException
import com.ctvhouse.ctvads.core.OpenRtbClient
import com.ctvhouse.ctvads.openrtb.Bid
import com.ctvhouse.ctvads.view.InterstitialActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Creative type carried by a fullscreen ad. */
enum class AdFormat { VIDEO, BANNER }

/**
 * Fullscreen (interstitial) ad. Requests a single bid for the chosen [AdFormat]
 * and, on [show], opens an opaque full-screen surface that renders it:
 * VAST video via ExoPlayer or an HTML creative in a WebView, with an ad timer
 * and a D-pad focusable skip control.
 *
 * Listeners are supplied up-front and **complement** each other — every one
 * receives every event (the SDK's own tracking runs first). Pass a base
 * listener in the constructor and add more with [addListener] instead of
 * overriding a single one. Use [AdCallbacks] to react to only some events:
 *
 * ```
 * val ad = InterstitialAd(context, AdCallbacks(
 *     onLoaded = { ad.show() },
 *     onCompleted = { goToContent() },
 * ))
 * ad.addListener(analyticsListener)   // дополняет, не заменяет
 * ad.load(lifecycleScope, AdFormat.VIDEO)
 * ```
 */
class InterstitialAd(
    private val context: Context,
    vararg listeners: AdListener,
) {

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<AdListener>().apply {
        addAll(listeners)
    }

    /** Placement/tag id sent as OpenRTB `imp.tagid`. Set via [setPlacementId]. */
    private var placementId: String? = null

    /**
     * Sets the placement/tag id sent as OpenRTB `imp.tagid` in the bid request to
     * identify this ad slot to the bidder. Fluent.
     */
    fun setPlacementId(placementId: String?): InterstitialAd {
        this.placementId = placementId
        return this
    }

    /**
     * Seconds before the "Skip" button becomes active. Set to 0 to allow
     * skipping immediately. Applies to both video and banner.
     */
    var skipOffsetSeconds: Int = 5

    /**
     * How long a fullscreen **banner** stays on screen before it auto-closes,
     * in seconds. Ignored for video (video length drives the timer).
     */
    var bannerDurationSeconds: Int = 15

    private val client = OpenRtbClient(context)
    private var loadedBid: Bid? = null
    private var loadedFormat: AdFormat? = null

    /** True once a bid has been loaded and is ready for [show]. */
    val isReady: Boolean get() = loadedBid != null

    /** Adds another listener that complements (does not replace) the existing ones. */
    fun addListener(listener: AdListener): InterstitialAd {
        listeners.add(listener)
        return this
    }

    /**
     * Requests a bid for [format]. On success every listener gets
     * [AdListener.onAdLoaded]; on failure [AdListener.onAdFailed].
     *
     * @param size     ad size used in the bid request. Defaults to the full screen.
     * @param autoShow when true, [show] is called automatically once loaded.
     */
    @JvmOverloads
    fun load(
        scope: CoroutineScope,
        format: AdFormat,
        size: AdSize = fullscreenSize(),
        bidFloor: Double? = null,
        autoShow: Boolean = false,
    ) {
        loadedFormat = format
        loadedBid = null
        scope.launch {
            val result = when (format) {
                AdFormat.VIDEO -> client.requestVideo(size, bidFloor, placementId)
                AdFormat.BANNER -> client.requestBanner(size, bidFloor, placementId)
            }
            result
                .onSuccess { bid ->
                    loadedBid = bid
                    listeners.forEach { it.onAdLoaded(bid) }
                    if (autoShow) show()
                }
                .onFailure { t ->
                    val error = (t as? AdException)?.error
                        ?: AdError.Render(t.message ?: "unknown", t)
                    listeners.forEach { it.onAdFailed(error) }
                }
        }
    }

    /**
     * Shows the previously [load]ed ad full-screen. No-op (reports
     * [AdError.NotConfigured]) if nothing is loaded yet.
     */
    fun show() {
        val bid = loadedBid
        val format = loadedFormat
        if (bid == null || format == null) {
            val error = AdError.NotConfigured("Interstitial is not loaded. Call load() first.")
            listeners.forEach { it.onAdFailed(error) }
            return
        }
        // Render/click/complete/dismiss come from the fullscreen activity — fan
        // them out to every listener.
        val combined = CompositeAdListener(*listeners.toTypedArray())
        val token = InterstitialStore.put(
            PendingInterstitial(
                bid = bid,
                format = format,
                listener = combined,
                skipOffsetSeconds = skipOffsetSeconds.coerceAtLeast(0),
                bannerDurationSeconds = bannerDurationSeconds.coerceAtLeast(1),
            )
        )
        context.startActivity(
            Intent(context, InterstitialActivity::class.java)
                .putExtra(InterstitialActivity.EXTRA_TOKEN, token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        // A loaded bid is single-use.
        loadedBid = null
    }

    private fun fullscreenSize(): AdSize {
        val dm = context.resources.displayMetrics
        val density = if (dm.density > 0f) dm.density else 1f
        val widthDp = (dm.widthPixels / density).toInt().coerceAtLeast(1)
        val heightDp = (dm.heightPixels / density).toInt().coerceAtLeast(1)
        return AdSize(widthDp, heightDp)
    }
}

/** Payload handed from [InterstitialAd.show] to [InterstitialActivity]. */
internal class PendingInterstitial(
    val bid: Bid,
    val format: AdFormat,
    val listener: AdListener?,
    val skipOffsetSeconds: Int,
    val bannerDurationSeconds: Int,
)

/**
 * In-process registry that passes the (non-parcelable) bid + listener to the
 * fullscreen activity without serializing them through the Intent.
 */
internal object InterstitialStore {
    private val pending = java.util.concurrent.ConcurrentHashMap<String, PendingInterstitial>()

    fun put(value: PendingInterstitial): String {
        val token = java.util.UUID.randomUUID().toString()
        pending[token] = value
        return token
    }

    fun take(token: String?): PendingInterstitial? =
        if (token == null) null else pending.remove(token)
}
