package com.ctvhouse.ctvads.view

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Opens an ad ClickThrough URL. Prefers launching a browser/app; if the device
 * has nothing that can handle the link (common on Android TV), it falls back to
 * a [QrCodeDialog] so the viewer can scan and open it on a phone.
 */
internal object ClickThroughOpener {

    /**
     * Opens [url]. Returns the [Dialog] when a QR fallback is shown (so the caller
     * can dismiss it to avoid leaking the window), or `null` when handled by an
     * external app or nothing was shown. When [erid] is set it is displayed in the
     * QR modal (ad-marking token).
     */
    fun open(context: Context, url: String, erid: String? = null): Dialog? {
        if (url.isBlank()) return null
        // Open only if a *real browser* exists. TV builds often ship a system
        // stub that "handles" web links but just shows "no app" — we detect it by
        // requiring CATEGORY_BROWSABLE and excluding the system/resolver package.
        if (hasBrowser(context, url) && tryStart(context, url)) return null
        return QrCodeDialog.show(context, url, erid)
    }

    private fun browserIntent(url: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)

    private fun hasBrowser(context: Context, url: String): Boolean = try {
        val handlers = context.packageManager.queryIntentActivities(browserIntent(url), 0)
        android.util.Log.i(
            "CtvAds",
            "ClickThrough handlers: " + handlers.joinToString { it.activityInfo?.packageName.orEmpty() },
        )
        handlers.any { info ->
            val pkg = info.activityInfo?.packageName.orEmpty()
            pkg.isNotEmpty() &&
                pkg != "android" &&
                !pkg.contains("resolver", ignoreCase = true) &&
                !pkg.contains("fallback", ignoreCase = true) &&
                // Android TV ships "frameworkpackagestubs" which claims to handle
                // web links but only shows a "no app" toast — treat it as no browser.
                !pkg.contains("stub", ignoreCase = true)
        }
    } catch (_: Throwable) {
        false
    }

    private fun tryStart(context: Context, url: String): Boolean = try {
        context.startActivity(browserIntent(url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Throwable) {
        false
    }
}

/** Modal dialog that renders [url] as a QR code with a focusable close button. */
internal object QrCodeDialog {

    fun show(context: Context, url: String, erid: String? = null): Dialog? {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val qr = generate(url, dp(220)) ?: return null

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0xFF16204A.toInt())
            }
        }

        content.addView(TextView(context).apply {
            text = "Отсканируйте QR, чтобы открыть на телефоне"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        })

        content.addView(ImageView(context).apply {
            setImageBitmap(qr)
            setBackgroundColor(Color.WHITE)
            val pad = dp(12)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dp(244), dp(244)).apply {
                topMargin = dp(20)
                bottomMargin = dp(16)
            }
        })

        content.addView(TextView(context).apply {
            text = url
            setTextColor(0xFF9AA3C7.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = 2
        })

        if (!erid.isNullOrBlank()) {
            content.addView(TextView(context).apply {
                text = "ERID: $erid"
                setTextColor(Color.WHITE)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
            })
        }

        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        content.addView(buildCloseButton(context, ::dp) { dialog.dismiss() })

        dialog.setContentView(
            content,
            LinearLayout.LayoutParams(dp(320), LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        dialog.show()
        return dialog
    }

    private fun buildCloseButton(
        context: Context,
        dp: (Int) -> Int,
        onClose: () -> Unit,
    ): View = TextView(context).apply {
        text = context.getString(android.R.string.cancel)
        setTextColor(Color.WHITE)
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(10), dp(24), dp(10))
        isFocusable = true
        isFocusableInTouchMode = true
        background = closeBg(dp, false)
        setOnFocusChangeListener { _, hasFocus -> background = closeBg(dp, hasFocus) }
        setOnClickListener { onClose() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(20) }
        post { requestFocus() }
    }

    private fun closeBg(dp: (Int) -> Int, focused: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(if (focused) 0xFF0B5FFF.toInt() else 0x33FFFFFF)
        if (focused) setStroke(dp(2), Color.WHITE)
    }

    private fun generate(text: String, sizePx: Int): Bitmap? = try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565).apply {
            for (x in 0 until w) {
                for (y in 0 until h) {
                    setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}
