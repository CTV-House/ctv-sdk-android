package com.ctvhouse.ctvsdk

/**
 * App-level ad configuration.
 *
 * TODO: replace [BIDDER_HOST] with your OpenRTB 2.5 bidder host and set the
 * publisher/app identifiers your bidder expects. The SDK derives `host/ads`
 * (auction) and `host/status` (technical check) from it.
 */
object AdConfig {
    const val BIDDER_HOST = "http://192.168.1.119:8080"

    const val APP_NAME = "CTV Demo"
    const val PUBLISHER_ID = "ctvhouse-demo"

    const val TEST_MODE = true
}
