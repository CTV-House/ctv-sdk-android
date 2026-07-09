package com.ctvhouse.ctvads.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleCoroutineScope
import com.ctvhouse.ctvads.AdError
import com.ctvhouse.ctvads.AdListener
import com.ctvhouse.ctvads.AdSize
import com.ctvhouse.ctvads.AdTrackingEvent
import com.ctvhouse.ctvads.core.AdException
import com.ctvhouse.ctvads.core.OpenRtbClient
import com.ctvhouse.ctvads.openrtb.Bid
import kotlinx.coroutines.launch

/**
 * Renders an OpenRTB HTML banner creative (`bid.adm`) inside a [WebView],
 * optimized for Android TV (D-pad focusable, focus highlight, opaque slot).
 *
 * The System WebView is not guaranteed on every Android TV device, so creation
 * is guarded and a missing WebView surfaces as [AdError.WebViewUnavailable]
 * instead of crashing the host.
 */
@SuppressLint("SetJavaScriptEnabled")
class BannerAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    var listener: AdListener? = null

    /** Additional listeners that complement [listener] (all receive events). */
    private val extraListeners = java.util.concurrent.CopyOnWriteArrayList<AdListener>()

    /** Adds a listener that complements (does not replace) [listener]. */
    fun addListener(listener: AdListener): BannerAdView {
        extraListeners.add(listener)
        return this
    }

    /**
     * When set, the ad slot is pinned to this size (in DIPs) regardless of the
     * dimensions the bidder reports. Useful for fixed overlay slots (e.g. a
     * 320×50 bottom banner).
     */
    var fixedSlotSize: AdSize? = null

    private val webView: WebView? = createWebView(context)
    private var currentBid: Bid? = null
    private val client = OpenRtbClient(context)
    private var viewableReported = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        setBackgroundColor(Color.WHITE)
        webView?.let { wv ->
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    currentBid?.let { emitRendered(it) }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                ): Boolean = openExternal(request?.url?.toString())

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                    openExternal(url)
            }
            addView(
                wv,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER)
            )
        }
        setOnClickListener { currentBid?.let { emitClicked(it) } }
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
    private fun emitTracking(event: AdTrackingEvent) {
        listener?.onTracking(event); extraListeners.forEach { it.onTracking(event) }
    }

    /** QR fallback dialog, dismissed on detach to avoid leaking the window. */
    private var clickDialog: android.app.Dialog? = null

    /** Routes a banner link tap to the browser, or a QR fallback on TV. */
    private fun openExternal(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        currentBid?.let { emitClicked(it) }
        clickDialog = ClickThroughOpener.open(context, url)
        return true
    }

    /** Fires a VIEWABLE tracking once the slot is actually visible on screen. */
    private fun reportViewableIfNeeded() {
        if (!viewableReported && currentBid != null && isShown) {
            viewableReported = true
            emitTracking(AdTrackingEvent.VIEWABLE)
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        reportViewableIfNeeded()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        reportViewableIfNeeded()
    }

    private fun createWebView(context: Context): WebView? = try {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = false
        }
    } catch (_: Throwable) {
        null
    }

    fun loadAd(
        scope: LifecycleCoroutineScope,
        size: AdSize,
        bidFloor: Double? = null,
        fixedSize: Boolean = false,
    ) {
        if (webView == null) {
            emitFailed(AdError.WebViewUnavailable)
            return
        }
        if (fixedSize) fixedSlotSize = size
        scope.launch {
            client.requestBanner(size, bidFloor)
                .onSuccess { bid -> render(bid) }
                .onFailure { t ->
                    val error = (t as? AdException)?.error ?: AdError.Render(t.message ?: "unknown", t)
                    emitFailed(error)
                }
        }
    }

    /** Renders a previously obtained [Bid] directly. */
    fun render(bid: Bid) {
        val wv = webView ?: run {
            emitFailed(AdError.WebViewUnavailable)
            return
        }
        val markup = bid.adm
        if (markup.isNullOrBlank()) {
            emitFailed(AdError.InvalidResponse("empty banner adm"))
            return
        }
        try {
            currentBid = bid
            viewableReported = false
            val fixed = fixedSlotSize
            if (fixed != null) applyCreativeSize(fixed.width, fixed.height)
            else applyCreativeSize(bid.w, bid.h)

            // Billing notice (burl): fired when the creative is actually rendered.
            client.fireNotice(bid.burl)
            emitTracking(AdTrackingEvent.IMPRESSION)
            wv.loadDataWithBaseURL(
                /* baseUrl = */ "https://localhost/",
                /* data = */ wrap(markup),
                /* mimeType = */ "text/html",
                /* encoding = */ "UTF-8",
                /* historyUrl = */ null,
            )
            // If the slot is already on screen, report viewability now.
            post { reportViewableIfNeeded() }
        } catch (t: Throwable) {
            emitFailed(AdError.Render("banner render failed", t))
        }
    }

    /**
     * Sizes the ad slot (OpenRTB w/h are in DIPs). Without this, a
     * `wrap_content` slot around a `match_parent` WebView collapses to 0×0.
     */
    private fun applyCreativeSize(w: Int?, h: Int?) {
        if (w == null || h == null || w <= 0 || h <= 0) return
        val density = resources.displayMetrics.density
        val widthPx = (w * density).toInt()
        val heightPx = (h * density).toInt()
        val lp = layoutParams
        if (lp != null) {
            lp.width = widthPx
            lp.height = heightPx
            layoutParams = lp
        } else {
            layoutParams = LayoutParams(widthPx, heightPx, Gravity.CENTER)
        }
    }

    override fun onDetachedFromWindow() {
        clickDialog?.let { if (it.isShowing) it.dismiss() }
        clickDialog = null
        webView?.stopLoading()
        webView?.destroy()
        super.onDetachedFromWindow()
    }

    override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: android.graphics.Rect?,
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        foreground = if (gainFocus) {
            android.graphics.drawable.GradientDrawable().apply { setStroke(6, Color.WHITE) }
        } else {
            null
        }
    }

    private fun wrap(adm: String): String = """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
        <style>
          html,body{margin:0;padding:0;height:100%;width:100%;
            display:flex;align-items:center;justify-content:center;
            background:transparent;overflow:hidden}
          img{max-width:100%;max-height:100%}
        </style>
        </head><body>$adm</body></html>
    """.trimIndent()
}
