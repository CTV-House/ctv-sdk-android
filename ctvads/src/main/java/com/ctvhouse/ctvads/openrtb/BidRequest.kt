package com.ctvhouse.ctvads.openrtb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenRTB 2.5 Bid Request object graph.
 *
 * Only the subset of fields relevant for a Connected-TV (CTV) banner/video
 * integration is modelled. Optional fields default to `null` and are omitted
 * from the serialized JSON (see [com.ctvhouse.ctvads.core.OpenRtbClient]).
 */
@Serializable
data class BidRequest(
    val id: String,
    val imp: List<Imp>,
    val app: App? = null,
    val device: Device? = null,
    val user: User? = null,
    val regs: Regs? = null,
    /** Auction type. 1 = First Price, 2 = Second Price Plus. */
    val at: Int = 2,
    /** Maximum time in ms the exchange allows for bids to be received. */
    val tmax: Int? = null,
    /** Allowed currencies, ISO-4217. */
    val cur: List<String>? = listOf("USD"),
    /** 1 = test mode (no billing). */
    val test: Int? = null,
)

@Serializable
data class Imp(
    val id: String,
    val banner: Banner? = null,
    val video: Video? = null,
    /** Minimum bid for this impression, CPM. */
    val bidfloor: Double? = null,
    val bidfloorcur: String? = "USD",
    /** 1 = HTTPS required. */
    val secure: Int? = 1,
    val tagid: String? = null,
)

@Serializable
data class Banner(
    val w: Int? = null,
    val h: Int? = null,
    val format: List<Format>? = null,
    /** Ad position on screen (OpenRTB 5.4). */
    val pos: Int? = null,
    val mimes: List<String>? = null,
    val api: List<Int>? = null,
)

@Serializable
data class Format(
    val w: Int,
    val h: Int,
)

@Serializable
data class Video(
    /** Supported content MIME types, e.g. "video/mp4". */
    val mimes: List<String>,
    val w: Int? = null,
    val h: Int? = null,
    val minduration: Int? = null,
    val maxduration: Int? = null,
    /** Supported video protocols (OpenRTB 5.8). 2=VAST2, 3=VAST3, 5=VAST2 Wrapper, 6=VAST3 Wrapper. */
    val protocols: List<Int>? = null,
    /** 1 = linear/in-stream. */
    val linearity: Int? = null,
    /** Placement type (OpenRTB 5.9). 1 = in-stream. */
    val placement: Int? = null,
    val skip: Int? = null,
    /** Playback methods (OpenRTB 5.10). */
    val playbackmethod: List<Int>? = null,
    val api: List<Int>? = null,
    val pos: Int? = null,
)

@Serializable
data class App(
    val id: String? = null,
    val name: String? = null,
    val bundle: String? = null,
    val domain: String? = null,
    val storeurl: String? = null,
    val cat: List<String>? = null,
    val ver: String? = null,
    val publisher: Publisher? = null,
)

@Serializable
data class Publisher(
    val id: String? = null,
    val name: String? = null,
)

@Serializable
data class Device(
    val ua: String? = null,
    val ip: String? = null,
    /** Device type (OpenRTB 5.21). 3 = Connected TV, 7 = Set-top box. */
    val devicetype: Int? = null,
    val make: String? = null,
    val model: String? = null,
    val os: String? = null,
    val osv: String? = null,
    val h: Int? = null,
    val w: Int? = null,
    val language: String? = null,
    /** Advertising ID (IFA). */
    val ifa: String? = null,
    /** Limit ad tracking. 1 = limited. */
    val lmt: Int? = null,
    /** Connection type (OpenRTB 5.22). */
    val connectiontype: Int? = null,
    val geo: Geo? = null,
)

@Serializable
data class Geo(
    val lat: Double? = null,
    val lon: Double? = null,
    /** ISO-3166-1 alpha-3 country code. */
    val country: String? = null,
)

@Serializable
data class User(
    val id: String? = null,
    val buyeruid: String? = null,
)

@Serializable
data class Regs(
    /** 1 = subject to COPPA. */
    val coppa: Int? = null,
)
