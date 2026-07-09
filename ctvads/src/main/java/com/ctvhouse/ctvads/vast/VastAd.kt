package com.ctvhouse.ctvads.vast

/**
 * A flattened representation of a single linear VAST creative, containing only
 * the data needed to play the ad and fire tracking. Produced by [VastResolver]
 * after wrapper resolution.
 */
data class VastAd(
    /** Progressive media file URL (e.g. MP4) for direct ExoPlayer playback. */
    val mediaFileUrl: String? = null,
    val impressions: List<String> = emptyList(),
    val trackingEvents: Map<String, List<String>> = emptyMap(),
    val clickThrough: String? = null,
    val clickTracking: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val durationMs: Long? = null,
    /** Russian ad-marking data from `<Extension type="nroa_inform">`, if present. */
    val nroa: NroaInform? = null,
) {
    /**
     * Ad-marking payload (ОРД/«nroa_inform»): the player must display [title]
     * as a marking label, open [url] when the label is activated (if the device
     * allows), and/or show the [erid] token of the creative.
     */
    data class NroaInform(
        val title: String? = null,
        val url: String? = null,
        val erid: String? = null,
    )

    /** Standard VAST linear tracking event names. */
    object Event {
        const val START = "start"
        const val FIRST_QUARTILE = "firstQuartile"
        const val MIDPOINT = "midpoint"
        const val THIRD_QUARTILE = "thirdQuartile"
        const val COMPLETE = "complete"
        const val SKIP = "skip"
    }
}
