package com.ctvhouse.ctvads.view

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.ctvhouse.ctvads.AdError
import com.ctvhouse.ctvads.AdFormat
import com.ctvhouse.ctvads.AdListener
import com.ctvhouse.ctvads.AdTrackingEvent
import com.ctvhouse.ctvads.InterstitialStore
import com.ctvhouse.ctvads.PendingInterstitial
import com.ctvhouse.ctvads.openrtb.Bid
import kotlin.math.ceil

/**
 * Opaque, full-screen surface for interstitial ads. Renders a VAST video
 * ([VideoAdView]) or an HTML banner ([BannerAdView]) centered on a black
 * backdrop, with an ad countdown timer and a D-pad focusable **Skip** button
 * that unlocks after a configurable offset. Not part of the public API —
 * launched by [com.ctvhouse.ctvads.InterstitialAd.show].
 */
class InterstitialActivity : Activity() {

    private var hostListener: AdListener? = null
    private var dismissed = false

    private var videoView: VideoAdView? = null
    private var adView: View? = null
    private lateinit var timerChip: TextView
    private lateinit var skipButton: TextView

    private var format: AdFormat = AdFormat.BANNER
    private var skipOffsetMs: Long = 5_000
    private var bannerDurationMs: Long = 15_000
    private var startedAtMs: Long = 0
    private var skippable = false

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateOverlay()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pending = InterstitialStore.take(intent.getStringExtra(EXTRA_TOKEN))
        if (pending == null) {
            finish()
            return
        }
        hostListener = pending.listener
        format = pending.format
        skipOffsetMs = pending.skipOffsetSeconds * 1000L
        bannerDurationMs = pending.bannerDurationSeconds * 1000L

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        setContentView(root)

        when (pending.format) {
            AdFormat.VIDEO -> renderVideo(root, pending)
            AdFormat.BANNER -> renderBanner(root, pending)
        }

        timerChip = buildTimerChip()
        skipButton = buildSkipButton()
        root.addView(timerChip)
        root.addView(skipButton)

        // Keep focus on the ad so D-pad OK triggers a click/ClickThrough. The
        // skip button is reachable via D-pad once it unlocks.
        adView?.let { av -> av.post { av.requestFocus() } }

        startedAtMs = SystemClock.elapsedRealtime()
        updateOverlay()
        handler.post(ticker)
    }

    private fun renderVideo(root: FrameLayout, pending: PendingInterstitial) {
        val view = VideoAdView(this)
        videoView = view
        adView = view
        view.listener = forwardingListener { finishAndDismiss() }
        root.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        view.render(pending.bid)
    }

    private fun renderBanner(root: FrameLayout, pending: PendingInterstitial) {
        val view = BannerAdView(this)
        adView = view
        view.listener = forwardingListener { /* banners don't auto-complete */ }
        root.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        view.render(pending.bid)
    }

    private fun forwardingListener(onCompleted: () -> Unit) = object : AdListener {
        override fun onAdRendered(bid: Bid) { hostListener?.onAdRendered(bid) }
        override fun onAdClicked(bid: Bid) { hostListener?.onAdClicked(bid) }
        override fun onAdFailed(error: AdError) {
            hostListener?.onAdFailed(error)
            finishAndDismiss()
        }
        override fun onAdCompleted(bid: Bid) {
            hostListener?.onAdCompleted(bid)
            onCompleted()
        }
        override fun onTracking(event: AdTrackingEvent) { hostListener?.onTracking(event) }
    }

    // ---- Overlay: timer + skip ----

    /** Recomputes the remaining time / skip state; auto-closes banners. */
    private fun updateOverlay() {
        val elapsed = when (format) {
            AdFormat.VIDEO -> videoView?.positionMs ?: 0L
            AdFormat.BANNER -> SystemClock.elapsedRealtime() - startedAtMs
        }
        val total = when (format) {
            AdFormat.VIDEO -> videoView?.durationMs ?: 0L
            AdFormat.BANNER -> bannerDurationMs
        }

        // Ad countdown + marking. If the creative carries nroa_inform, its Title
        // replaces the default "Реклама" word (right next to the counter) and the
        // chip becomes a clickable marking that opens the marking URL.
        val remainingMs = (total - elapsed).coerceAtLeast(0L)
        val nroa = if (format == AdFormat.VIDEO) videoView?.nroaInform else null
        val hasUrl = !nroa?.url.isNullOrBlank()
        setupMarkingChip(hasUrl)
        val label = nroa?.title?.takeIf { it.isNotBlank() } ?: "Реклама"
        val marking = if (hasUrl) "$label \u2197" else label
        timerChip.text = if (total > 0) "$marking \u00B7 ${secs(remainingMs)} с" else marking

        // Skip button state.
        if (!skippable) {
            val toSkip = (skipOffsetMs - elapsed).coerceAtLeast(0L)
            if (toSkip <= 0L) {
                skippable = true
            } else {
                skipButton.text = "Пропустить через ${secs(toSkip)}"
            }
        }
        if (skippable) {
            skipButton.text = "Пропустить \u203A"
            skipButton.isEnabled = true
            skipButton.alpha = 1f
        }

        // Banner auto-close when its display time elapses.
        if (format == AdFormat.BANNER && total > 0 && elapsed >= total) {
            finishAndDismiss()
        }
    }

    private var markingSetup = false

    /** Makes the timer chip a focusable, clickable marking (once) when it has a URL. */
    private fun setupMarkingChip(hasUrl: Boolean) {
        if (markingSetup || !hasUrl) return
        markingSetup = true
        timerChip.isFocusable = true
        timerChip.isFocusableInTouchMode = true
        timerChip.setOnFocusChangeListener { _, f -> timerChip.background = chipBackground(f) }
        timerChip.setOnClickListener { videoView?.openMarkingUrl() }
    }

    private fun secs(ms: Long): Int = ceil(ms / 1000.0).toInt()

    private fun buildTimerChip(): TextView {
        val m = dp(24)
        val padH = dp(16)
        val padV = dp(8)
        return TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(padH, padV, padH, padV)
            background = chipBackground(false)
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            ).apply { setMargins(m, m, m, m) }
        }
    }

    private fun buildSkipButton(): TextView {
        val m = dp(24)
        val padH = dp(20)
        val padV = dp(10)
        return TextView(this).apply {
            text = "Пропустить"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(padH, padV, padH, padV)
            isFocusable = true
            isFocusableInTouchMode = true
            isEnabled = false
            alpha = 0.6f
            background = chipBackground(false)
            setOnFocusChangeListener { _, hasFocus -> background = chipBackground(hasFocus) }
            setOnClickListener { if (skippable) onSkip() }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply { setMargins(m, m, m, m) }
        }
    }

    private fun onSkip() {
        if (format == AdFormat.VIDEO) videoView?.fireSkipTracking()
        finishAndDismiss()
    }

    private fun chipBackground(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(if (focused) 0xFF0B5FFF.toInt() else 0xB3000000.toInt())
        if (focused) setStroke(dp(2), Color.WHITE)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isSelect = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        val skipFocused = ::skipButton.isInitialized && skipButton.isFocused
        // OK on the marking chip = let it handle it (opens the marking URL).
        val markingFocused = ::timerChip.isInitialized && timerChip.isFocused
        // OK on the skip button/marking = handled there; OK elsewhere = click the ad.
        if (isSelect && event.action == KeyEvent.ACTION_DOWN && !skipFocused && !markingFocused) {
            when (format) {
                AdFormat.VIDEO -> videoView?.performAdClick()
                AdFormat.BANNER -> adView?.performClick()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private var focusLostAt = 0L

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // While a ClickThrough (QR modal / external app) is on top, freeze the
        // banner countdown so the ad isn't auto-closed under the dialog.
        if (!hasFocus) {
            focusLostAt = SystemClock.elapsedRealtime()
        } else if (focusLostAt > 0L) {
            startedAtMs += SystemClock.elapsedRealtime() - focusLostAt
            focusLostAt = 0L
        }
    }

    override fun onBackPressed() {
        // Block back until the ad is skippable, then treat it as skip.
        if (skippable) onSkip()
    }

    private fun finishAndDismiss() {
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        videoView?.releasePlayer()
        if (!dismissed) {
            dismissed = true
            hostListener?.onAdDismissed()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TOKEN = "ctv_interstitial_token"
    }
}
