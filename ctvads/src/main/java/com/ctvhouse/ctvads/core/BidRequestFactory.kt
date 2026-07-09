package com.ctvhouse.ctvads.core

import android.content.Context
import com.ctvhouse.ctvads.AdSize
import com.ctvhouse.ctvads.CtvAdsConfig
import com.ctvhouse.ctvads.openrtb.App
import com.ctvhouse.ctvads.openrtb.Banner
import com.ctvhouse.ctvads.openrtb.BidRequest
import com.ctvhouse.ctvads.openrtb.Format
import com.ctvhouse.ctvads.openrtb.Imp
import com.ctvhouse.ctvads.openrtb.Publisher
import com.ctvhouse.ctvads.openrtb.Video
import java.util.UUID

/**
 * Assembles OpenRTB 2.5 [BidRequest]s for banner and video impressions on CTV.
 */
internal class BidRequestFactory(
    private val context: Context,
    private val config: CtvAdsConfig,
) {

    fun banner(size: AdSize, bidFloor: Double?, placementId: String? = null): BidRequest = base(
        imp = Imp(
            id = "1",
            banner = Banner(
                w = size.width,
                h = size.height,
                format = listOf(Format(size.width, size.height)),
                pos = 7, // full screen
            ),
            bidfloor = bidFloor,
            tagid = placementId,
        )
    )

    fun video(size: AdSize, bidFloor: Double?, placementId: String? = null): BidRequest = base(
        imp = Imp(
            id = "1",
            video = Video(
                mimes = listOf("video/mp4", "video/webm"),
                w = size.width,
                h = size.height,
                minduration = 5,
                maxduration = 60,
                protocols = listOf(2, 3, 5, 6), // VAST 2/3 + wrappers
                linearity = 1,
                placement = 1, // in-stream
                playbackmethod = listOf(1), // autoplay, sound on
                pos = 7,
            ),
            bidfloor = bidFloor,
            tagid = placementId,
        )
    )

    private fun base(imp: Imp): BidRequest = BidRequest(
        id = UUID.randomUUID().toString(),
        imp = listOf(imp),
        app = App(
            name = config.appName,
            bundle = config.appBundle,
            storeurl = config.appStoreUrl,
            domain = config.appDomain,
            publisher = config.publisherId?.let { Publisher(id = it) },
        ),
        device = DeviceInfo.build(context),
        tmax = config.timeoutMs.toInt(),
        test = if (config.test) 1 else null,
    )
}
