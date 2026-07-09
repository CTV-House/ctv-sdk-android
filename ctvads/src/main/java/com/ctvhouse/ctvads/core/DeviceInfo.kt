package com.ctvhouse.ctvads.core

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import com.ctvhouse.ctvads.openrtb.Device
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import java.util.Locale

/**
 * Builds an OpenRTB 2.5 [Device] object describing the current Android TV device.
 *
 * The advertising identifier is resolved from Google Play Services
 * ([AdvertisingIdClient]) when available, honoring the user's "Limit ad
 * tracking" setting (`lmt`). On devices without Play Services (many AOSP-based
 * TVs) it falls back to `ANDROID_ID` with `lmt = 0`.
 *
 * IMPORTANT: [build] performs blocking I/O (GAID lookup) and MUST be called off
 * the main thread. [OpenRtbClient] invokes it on `Dispatchers.IO`.
 */
internal object DeviceInfo {

    /** `device.devicetype`: 3 = Connected TV. */
    private const val DEVICE_TYPE_CONNECTED_TV = 3

    fun build(context: Context): Device {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        val advertising = resolveAdvertisingId(context)
        return Device(
            ua = System.getProperty("http.agent"),
            devicetype = DEVICE_TYPE_CONNECTED_TV,
            make = Build.MANUFACTURER,
            model = Build.MODEL,
            os = "Android",
            osv = Build.VERSION.RELEASE,
            w = metrics.widthPixels,
            h = metrics.heightPixels,
            language = Locale.getDefault().language,
            ifa = advertising.ifa,
            lmt = advertising.lmt,
            connectiontype = 1,
        )
    }

    private data class Advertising(val ifa: String?, val lmt: Int)

    private fun resolveAdvertisingId(context: Context): Advertising = try {
        val info = AdvertisingIdClient.getAdvertisingIdInfo(context)
        val lmt = if (info.isLimitAdTrackingEnabled) 1 else 0
        Advertising(ifa = info.id, lmt = lmt)
    } catch (_: Throwable) {
        // Play Services missing/unavailable — fall back to a stable device id.
        Advertising(ifa = androidIdFallback(context), lmt = 0)
    }

    @Suppress("HardwareIds")
    private fun androidIdFallback(context: Context): String? = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    } catch (_: Exception) {
        null
    }
}
