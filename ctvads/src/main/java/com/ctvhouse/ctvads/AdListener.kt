package com.ctvhouse.ctvads

import com.ctvhouse.ctvads.openrtb.Bid

/**
 * Lifecycle callbacks for banner and video ads. All callbacks are delivered on
 * the main thread.
 */
interface AdListener {
    /**
     * A bid was won and cached, ready to be shown. Used by fullscreen
     * (interstitial) ads to signal that [InterstitialAd.show] can be called.
     */
    fun onAdLoaded(bid: Bid) {}

    /** A bid was won and the creative was successfully rendered/started. */
    fun onAdRendered(bid: Bid) {}

    /** No bid was returned, or the creative failed to load/render. */
    fun onAdFailed(error: AdError) {}

    /** User clicked/selected the ad (D-pad OK). Kept separate from tracking. */
    fun onAdClicked(bid: Bid) {}

    /** Video only: playback finished (either completed or user skipped). */
    fun onAdCompleted(bid: Bid) {}

    /** Fullscreen only: the ad surface was closed and control returns to the app. */
    fun onAdDismissed() {}

    /**
     * A tracking event fired by the SDK: impression, viewability and video
     * quartile/start/complete/skip events. Clicks are delivered via
     * [onAdClicked], not here. Called after the corresponding pixel/URL is fired.
     */
    fun onTracking(event: AdTrackingEvent) {}
}

/** Tracking events reported through [AdListener.onTracking] (clicks excluded). */
enum class AdTrackingEvent {
    /** Creative impression counted (`nurl`/`burl` + VAST `Impression`). */
    IMPRESSION,

    /** Creative became viewable on screen. */
    VIEWABLE,

    /** Video: playback started. */
    START,

    /** Video: 25% played. */
    FIRST_QUARTILE,

    /** Video: 50% played. */
    MIDPOINT,

    /** Video: 75% played. */
    THIRD_QUARTILE,

    /** Video: playback completed. */
    COMPLETE,

    /** Video: user skipped the ad. */
    SKIP,
}

sealed class AdError(val message: String, val cause: Throwable? = null) {
    object NoBid : AdError("No bid returned by the bidder")
    class Network(cause: Throwable) : AdError("Network error: ${cause.message}", cause)
    class InvalidResponse(msg: String) : AdError("Invalid bid response: $msg")
    class Render(msg: String, cause: Throwable? = null) : AdError("Render error: $msg", cause)
    class NotConfigured(msg: String) : AdError(msg)

    /** The device has no usable System WebView (can happen on some Android TVs). */
    object WebViewUnavailable : AdError("System WebView is unavailable on this device")

    /** The bidder reported `stopped` via `/status`; ad requests are blocked. */
    object Stopped : AdError("Bidder is stopped (disabled via /status)")
}
