package com.ctvhouse.ctvads.view

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.lifecycle.LifecycleCoroutineScope
import com.ctvhouse.ctvads.AdError
import com.ctvhouse.ctvads.AdListener
import com.ctvhouse.ctvads.AdSize
import com.ctvhouse.ctvads.AdTrackingEvent
import com.ctvhouse.ctvads.core.AdException
import com.ctvhouse.ctvads.core.OpenRtbClient
import com.ctvhouse.ctvads.openrtb.Bid
import com.ctvhouse.ctvads.vast.VastAd
import com.ctvhouse.ctvads.vast.VastResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Plays a linear video ad returned by the bidder (`bid.adm`). Resolves VAST
 * (including **wrapper** redirects) and plays the progressive MediaFile with
 * ExoPlayer, firing standard VAST quartile tracking. Designed for full-screen
 * playback on TV. VPAID creatives are not supported.
 */
class VideoAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    var listener: AdListener? = null

    /** Additional listeners that complement [listener] (all receive events). */
    private val extraListeners = java.util.concurrent.CopyOnWriteArrayList<AdListener>()

    /** Adds a listener that complements (does not replace) [listener]. */
    fun addListener(listener: AdListener): VideoAdView {
        extraListeners.add(listener)
        return this
    }

    private val playerView = PlayerView(context).apply {
        useController = false
        setKeepContentOnPlayerReset(true)
    }
    private var player: ExoPlayer? = null

    private val client = OpenRtbClient(context)
    private val resolver = VastResolver()
    private val handler = Handler(Looper.getMainLooper())
    private val viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentBid: Bid? = null
    private var vast: VastAd? = null
    private val firedEvents = mutableSetOf<String>()

    /** Ad-marking payload (ОРД / "nroa_inform") from the resolved VAST, if any. */
    val nroaInform: VastAd.NroaInform? get() = vast?.nroa

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        addView(playerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /**
     * Opens the marking (nroa) ClickThrough URL, pausing playback. The ERID is
     * shown in the QR modal when the device has no browser. Playback resumes when
     * the window regains focus.
     */
    fun openMarkingUrl() {
        val nroa = vast?.nroa ?: return
        val url = nroa.url
        if (url.isNullOrBlank()) return
        pauseAd()
        clickDialog = ClickThroughOpener.open(context, url, nroa.erid)
    }

    fun loadAd(scope: LifecycleCoroutineScope, size: AdSize, bidFloor: Double? = null) {
        scope.launch {
            client.requestVideo(size, bidFloor)
                .onSuccess { bid -> handleBid(bid) }
                .onFailure { t ->
                    val error = (t as? AdException)?.error ?: AdError.Render(t.message ?: "unknown", t)
                    emitFailed(error)
                }
        }
    }

    // Dispatch to the configured listener first, then any complementary ones.
    private fun emitRendered(bid: Bid) {
        listener?.onAdRendered(bid); extraListeners.forEach { it.onAdRendered(bid) }
    }
    private fun emitFailed(error: AdError) {
        listener?.onAdFailed(error); extraListeners.forEach { it.onAdFailed(error) }
    }
    private fun emitClicked(bid: Bid) {
        listener?.onAdClicked(bid); extraListeners.forEach { it.onAdClicked(bid) }
    }
    private fun emitCompleted(bid: Bid) {
        listener?.onAdCompleted(bid); extraListeners.forEach { it.onAdCompleted(bid) }
    }
    private fun emitTracking(event: AdTrackingEvent) {
        listener?.onTracking(event); extraListeners.forEach { it.onTracking(event) }
    }

    /** Resolves and renders a previously obtained [Bid]. */
    fun render(bid: Bid) {
        handleBid(bid)
    }

    /** Current playback position in ms (0 if not playing yet). */
    val positionMs: Long
        get() = player?.currentPosition?.coerceAtLeast(0L) ?: 0L

    /** Ad duration in ms: player duration if known, else the VAST `Duration`. */
    val durationMs: Long
        get() {
            val d = player?.duration ?: androidx.media3.common.C.TIME_UNSET
            return if (d > 0) d else (vast?.durationMs ?: 0L)
        }

    /** Fires VAST `skip` tracking (call when the user skips the ad). */
    fun fireSkipTracking() = fireEvent(VastAd.Event.SKIP)

    private fun handleBid(bid: Bid) {
        val markup = bid.adm
        if (markup.isNullOrBlank()) {
            emitFailed(AdError.InvalidResponse("empty video adm"))
            return
        }
        viewScope.launch {
            val resolved = try {
                resolver.resolve(markup)
            } catch (t: Throwable) {
                emitFailed(AdError.Render("VAST resolve failed", t))
                return@launch
            }
            if (resolved == null) {
                emitFailed(AdError.InvalidResponse("could not resolve VAST"))
                return@launch
            }
            currentBid = bid
            vast = resolved
            firedEvents.clear()
            playProgressive(resolved)
        }
    }

    // ---- Progressive (ExoPlayer) path ----

    private fun playProgressive(ad: VastAd) {
        val url = ad.mediaFileUrl
        if (url.isNullOrBlank()) {
            emitFailed(AdError.InvalidResponse("no playable media file"))
            return
        }
        releasePlayer()
        playerView.visibility = VISIBLE
        val exo = try {
            ExoPlayer.Builder(context).build()
        } catch (t: Throwable) {
            emitFailed(AdError.Render("player init failed", t))
            return
        }
        player = exo
        playerView.player = exo

        exo.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> onReady()
                    Player.STATE_ENDED -> onEnded()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                emitFailed(AdError.Render("playback error", error))
            }
        })
        exo.prepare()
        exo.playWhenReady = true
    }

    private var reported = false
    private fun onReady() {
        if (!reported) {
            reported = true
            currentBid?.let { emitRendered(it) }
            // Billing notice (burl): fired when the creative is actually rendered.
            client.fireNotice(currentBid?.burl)
            vast?.impressions?.forEach { client.fireNotice(it) }
            vast?.trackingEvents?.get("creativeView")?.forEach { client.fireNotice(it) }
            emitTracking(AdTrackingEvent.IMPRESSION)
            // The video is on screen and playing, so it's viewable at this point.
            emitTracking(AdTrackingEvent.VIEWABLE)
            startQuartileTracking()
        }
    }

    private fun onEnded() {
        fireEvent(VastAd.Event.COMPLETE)
        currentBid?.let { emitCompleted(it) }
        stopQuartileTracking()
    }

    private val quartileTicker = object : Runnable {
        override fun run() {
            val exo = player ?: return
            val duration = exo.duration
            if (duration > 0) {
                val progress = exo.currentPosition.toFloat() / duration
                when {
                    progress >= 0.75f -> fireEvent(VastAd.Event.THIRD_QUARTILE)
                    progress >= 0.50f -> fireEvent(VastAd.Event.MIDPOINT)
                    progress >= 0.25f -> fireEvent(VastAd.Event.FIRST_QUARTILE)
                    progress > 0f -> fireEvent(VastAd.Event.START)
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    private fun startQuartileTracking() = handler.post(quartileTicker)
    private fun stopQuartileTracking() = handler.removeCallbacks(quartileTicker)

    private fun fireEvent(event: String) {
        if (firedEvents.add(event)) {
            vast?.trackingEvents?.get(event)?.forEach { client.fireNotice(it) }
            trackingEventOf(event)?.let { emitTracking(it) }
        }
    }

    private fun trackingEventOf(event: String): AdTrackingEvent? = when (event) {
        VastAd.Event.START -> AdTrackingEvent.START
        VastAd.Event.FIRST_QUARTILE -> AdTrackingEvent.FIRST_QUARTILE
        VastAd.Event.MIDPOINT -> AdTrackingEvent.MIDPOINT
        VastAd.Event.THIRD_QUARTILE -> AdTrackingEvent.THIRD_QUARTILE
        VastAd.Event.COMPLETE -> AdTrackingEvent.COMPLETE
        VastAd.Event.SKIP -> AdTrackingEvent.SKIP
        else -> null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            handleClick()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Triggers a click programmatically (e.g. from a hosting fullscreen activity). */
    fun performAdClick() = handleClick()

    private fun handleClick() {
        val bid = currentBid ?: return
        vast?.clickTracking?.forEach { client.fireNotice(it) }
        emitClicked(bid)
        val url = vast?.clickThrough
        if (url.isNullOrBlank()) return
        // Pause while the user is on the ClickThrough (QR modal / external app);
        // playback resumes automatically once this window regains focus.
        pauseAd()
        clickDialog = ClickThroughOpener.open(context, url)
    }

    /** QR fallback dialog, dismissed on detach to avoid leaking the window. */
    private var clickDialog: android.app.Dialog? = null

    private var pausedForClick = false

    /** Pauses playback (e.g. while a ClickThrough target is shown). */
    fun pauseAd() {
        val exo = player ?: return
        if (exo.playbackState == Player.STATE_ENDED) return
        pausedForClick = true
        exo.playWhenReady = false
    }

    /** Resumes playback if it was paused for a click. */
    fun resumeAd() {
        val exo = player ?: return
        if (!pausedForClick) return
        pausedForClick = false
        if (exo.playbackState != Player.STATE_ENDED) exo.playWhenReady = true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // Regained focus after the QR dialog closed or the browser was dismissed.
        if (hasWindowFocus) resumeAd()
    }

    fun releasePlayer() {
        stopQuartileTracking()
        player?.release()
        player = null
        playerView.player = null
        reported = false
    }

    override fun onDetachedFromWindow() {
        clickDialog?.let { if (it.isShowing) it.dismiss() }
        clickDialog = null
        releasePlayer()
        viewScope.cancel()
        super.onDetachedFromWindow()
    }
}
