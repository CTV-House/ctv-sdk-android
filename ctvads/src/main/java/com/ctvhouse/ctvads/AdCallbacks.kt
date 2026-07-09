package com.ctvhouse.ctvads

import com.ctvhouse.ctvads.openrtb.Bid

/**
 * Lightweight [AdListener] built from optional lambdas, so callers can react to
 * only the events they care about instead of implementing the whole interface.
 *
 * ```
 * banner.listener = AdCallbacks(
 *     onRendered = { bid -> analytics.impression(bid) },
 *     onFailed = { err -> log(err) },
 * )
 * ```
 */
class AdCallbacks(
    private val onLoaded: ((Bid) -> Unit)? = null,
    private val onRendered: ((Bid) -> Unit)? = null,
    private val onFailed: ((AdError) -> Unit)? = null,
    private val onClicked: ((Bid) -> Unit)? = null,
    private val onCompleted: ((Bid) -> Unit)? = null,
    private val onDismissed: (() -> Unit)? = null,
    private val onTracking: ((AdTrackingEvent) -> Unit)? = null,
) : AdListener {
    override fun onAdLoaded(bid: Bid) { onLoaded?.invoke(bid) }
    override fun onAdRendered(bid: Bid) { onRendered?.invoke(bid) }
    override fun onAdFailed(error: AdError) { onFailed?.invoke(error) }
    override fun onAdClicked(bid: Bid) { onClicked?.invoke(bid) }
    override fun onAdCompleted(bid: Bid) { onCompleted?.invoke(bid) }
    override fun onAdDismissed() { onDismissed?.invoke() }
    override fun onTracking(event: AdTrackingEvent) { onTracking?.invoke(event) }
}

/**
 * Fans every callback out to each of [listeners] in order (nulls skipped).
 * Used to run per-call `loadAd(...)` lambdas *after* the view's own
 * [AdListener], so the SDK finishes its work (rendering, tracking pings) before
 * the caller's extra handlers run.
 */
internal class CompositeAdListener(private vararg val listeners: AdListener?) : AdListener {
    override fun onAdLoaded(bid: Bid) { listeners.forEach { it?.onAdLoaded(bid) } }
    override fun onAdRendered(bid: Bid) { listeners.forEach { it?.onAdRendered(bid) } }
    override fun onAdFailed(error: AdError) { listeners.forEach { it?.onAdFailed(error) } }
    override fun onAdClicked(bid: Bid) { listeners.forEach { it?.onAdClicked(bid) } }
    override fun onAdCompleted(bid: Bid) { listeners.forEach { it?.onAdCompleted(bid) } }
    override fun onAdDismissed() { listeners.forEach { it?.onAdDismissed() } }
    override fun onTracking(event: AdTrackingEvent) { listeners.forEach { it?.onTracking(event) } }
}
