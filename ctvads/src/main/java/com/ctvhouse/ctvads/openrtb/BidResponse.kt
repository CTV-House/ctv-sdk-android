package com.ctvhouse.ctvads.openrtb

import kotlinx.serialization.Serializable

/**
 * OpenRTB 2.5 Bid Response object graph.
 */
@Serializable
data class BidResponse(
    val id: String? = null,
    val seatbid: List<SeatBid>? = null,
    val bidid: String? = null,
    val cur: String? = null,
    /** No-bid reason code (OpenRTB 5.24). */
    val nbr: Int? = null,
)

@Serializable
data class SeatBid(
    val bid: List<Bid> = emptyList(),
    val seat: String? = null,
)

@Serializable
data class Bid(
    val id: String? = null,
    val impid: String,
    /** Bid price, CPM. */
    val price: Double = 0.0,
    /** Win notice URL. Should be pinged on render. */
    val nurl: String? = null,
    /** Billing notice URL. */
    val burl: String? = null,
    /** Loss notice URL. */
    val lurl: String? = null,
    /** Ad markup: HTML for banners, VAST XML for video. */
    val adm: String? = null,
    val adid: String? = null,
    /** Advertiser domains. */
    val adomain: List<String>? = null,
    /** Creative ID. */
    val crid: String? = null,
    val cid: String? = null,
    val w: Int? = null,
    val h: Int? = null,
)
