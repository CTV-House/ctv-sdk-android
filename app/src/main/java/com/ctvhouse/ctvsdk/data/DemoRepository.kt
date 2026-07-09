package com.ctvhouse.ctvsdk.data

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Loads demo channels and movie catalog from JSON files bundled in `assets/`
 * and exposes them as home-screen [Rail]s.
 */
class DemoRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Channel rails (grouped by category) followed by movie rails (by genre). */
    fun rails(): List<Rail> {
        val demoRail = Rail(
            "Демо рекламы",
            listOf(
                Card.ActionCard("Fullscreen видео", "VAST / MP4", ActionKind.FULLSCREEN_VIDEO),
                Card.ActionCard("Fullscreen баннер", "HTML", ActionKind.FULLSCREEN_BANNER),
            ),
        )

        val channelRails = channels()
            .groupBy { it.category }
            .map { (category, items) ->
                Rail(category, items.map { Card.ChannelCard(it) })
            }

        val movieRails = movies()
            .groupBy { it.genre }
            .map { (genre, items) ->
                Rail(genre, items.map { Card.MovieCard(it) })
            }

        return listOf(demoRail) + channelRails + movieRails
    }

    private fun channels(): List<Channel> =
        readAsset("channels.json")
            .let { json.decodeFromString(ChannelsFile.serializer(), it) }
            .channels

    private fun movies(): List<Movie> =
        readAsset("catalog.json")
            .let { json.decodeFromString(CatalogFile.serializer(), it) }
            .movies

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }
}
