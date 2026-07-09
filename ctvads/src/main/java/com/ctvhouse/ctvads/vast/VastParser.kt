package com.ctvhouse.ctvads.vast

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * Parses a single VAST document (one level) into a [ParsedVast]. Wrapper
 * resolution and cross-document merging are handled by [VastResolver].
 *
 * Supports VAST 2/3/4 inline & wrapper linear ads with progressive MediaFiles.
 * VPAID creatives are intentionally not supported and are skipped.
 */
internal object VastParser {

    /** Raw, single-document parse result. */
    data class ParsedVast(
        val wrapperTagUri: String? = null,
        val followAdditionalWrappers: Boolean = true,
        val mediaFileUrl: String? = null,
        val impressions: List<String> = emptyList(),
        val trackingEvents: Map<String, List<String>> = emptyMap(),
        val clickThrough: String? = null,
        val clickTracking: List<String> = emptyList(),
        val errors: List<String> = emptyList(),
        val durationMs: Long? = null,
        val nroa: VastAd.NroaInform? = null,
    )

    private data class MediaCandidate(
        val url: String,
        val type: String,
        val bitrate: Int,
        val width: Int,
        val apiFramework: String,
    )

    fun parse(xml: String): ParsedVast? {
        return try {
            // Bidders sometimes prepend a BOM / newline / whitespace before the
            // `<?xml …?>` declaration, which is a fatal XML error. Strip anything
            // before the first tag so such creatives still parse.
            val sanitized = sanitize(xml)
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(StringReader(sanitized))
            }

            var wrapperTagUri: String? = null
            var followWrappers = true
            val impressions = mutableListOf<String>()
            val tracking = mutableMapOf<String, MutableList<String>>()
            val clickTracking = mutableListOf<String>()
            val errors = mutableListOf<String>()
            val mediaFiles = mutableListOf<MediaCandidate>()
            var clickThrough: String? = null
            var durationMs: Long? = null
            var nroa: VastAd.NroaInform? = null

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "Wrapper" -> {
                            followWrappers =
                                parser.getAttributeValue(null, "followAdditionalWrappers") != "false"
                        }
                        "VASTAdTagURI" -> wrapperTagUri = readText(parser)
                        "Impression" -> readText(parser)?.let { impressions.add(it) }
                        "Error" -> readText(parser)?.let { errors.add(it) }
                        "Duration" -> durationMs = parseDuration(readText(parser))
                        "Tracking" -> {
                            val evt = parser.getAttributeValue(null, "event")
                            val url = readText(parser)
                            if (evt != null && url != null) {
                                tracking.getOrPut(evt) { mutableListOf() }.add(url)
                            }
                        }
                        "ClickThrough" -> clickThrough = readText(parser)
                        "ClickTracking" -> readText(parser)?.let { clickTracking.add(it) }
                        "Extension" -> {
                            if (parser.getAttributeValue(null, "type") == "nroa_inform") {
                                readNroa(parser)?.let { nroa = it }
                            }
                        }
                        "MediaFile" -> {
                            val type = parser.getAttributeValue(null, "type").orEmpty()
                            val api = parser.getAttributeValue(null, "apiFramework").orEmpty()
                            val bitrate = parser.getAttributeValue(null, "bitrate")?.toIntOrNull() ?: 0
                            val width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                            val url = readText(parser)
                            if (url != null) {
                                mediaFiles.add(MediaCandidate(url, type, bitrate, width, api))
                            }
                        }
                    }
                }
                event = parser.next()
            }

            val progressive = pickProgressive(mediaFiles)

            ParsedVast(
                wrapperTagUri = wrapperTagUri,
                followAdditionalWrappers = followWrappers,
                mediaFileUrl = progressive,
                impressions = impressions,
                trackingEvents = tracking,
                clickThrough = clickThrough,
                clickTracking = clickTracking,
                errors = errors,
                durationMs = durationMs,
                nroa = nroa,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads a `<Extension type="nroa_inform">` block (Title/Url/Erid), leaving the
     * parser positioned on the closing `</Extension>` tag.
     */
    private fun readNroa(parser: XmlPullParser): VastAd.NroaInform? {
        var title: String? = null
        var url: String? = null
        var erid: String? = null
        while (true) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> if (parser.name == "Extension") break
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.START_TAG -> when (parser.name) {
                    "Title" -> title = readText(parser)
                    "Url" -> url = readText(parser)
                    "Erid" -> erid = readText(parser)
                }
            }
        }
        return if (title != null || url != null || erid != null) {
            VastAd.NroaInform(title = title, url = url, erid = erid)
        } else {
            null
        }
    }

    /** Prefer MP4, then highest bitrate. Ignores VPAID entries. */
    private fun pickProgressive(files: List<MediaCandidate>): String? {
        return files
            .filter { !it.apiFramework.equals("VPAID", ignoreCase = true) }
            .sortedWith(
                compareByDescending<MediaCandidate> { it.type.contains("mp4") }
                    .thenByDescending { it.bitrate }
                    .thenByDescending { it.width }
            )
            .firstOrNull()
            ?.url
    }

    /** Drops a BOM and any leading characters before the first `<` tag. */
    private fun sanitize(xml: String): String {
        val noBom = xml.removePrefix("\uFEFF")
        val start = noBom.indexOf('<')
        return if (start > 0) noBom.substring(start) else noBom
    }

    /**
     * Reads the concatenated character data of the current element (text +
     * CDATA), leaving the parser positioned on the closing tag. Robust to
     * surrounding whitespace and to text/CDATA being reported as separate tokens.
     */
    private fun readText(parser: XmlPullParser): String? {
        val sb = StringBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.TEXT -> parser.text?.let { sb.append(it) }
                else -> break
            }
        }
        return sb.toString().trim().takeIf { it.isNotBlank() }
    }

    /** Parses `HH:MM:SS(.mmm)` into milliseconds. */
    private fun parseDuration(value: String?): Long? {
        val parts = value?.split(":") ?: return null
        if (parts.size != 3) return null
        return try {
            val h = parts[0].toLong()
            val m = parts[1].toLong()
            val s = parts[2].toDouble()
            ((h * 3600 + m * 60) * 1000 + (s * 1000).toLong())
        } catch (_: Exception) {
            null
        }
    }
}
