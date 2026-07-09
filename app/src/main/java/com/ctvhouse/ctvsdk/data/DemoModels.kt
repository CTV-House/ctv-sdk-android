package com.ctvhouse.ctvsdk.data

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val category: String,
    val logoUrl: String,
    val streamUrl: String,
)

@Serializable
data class Movie(
    val id: String,
    val title: String,
    val year: Int,
    val genre: String,
    val posterUrl: String,
    val description: String,
    val videoUrl: String,
)

@Serializable
internal data class ChannelsFile(val channels: List<Channel>)

@Serializable
internal data class CatalogFile(val movies: List<Movie>)

/** A single card on the home screen. */
sealed interface Card {
    data class ChannelCard(val channel: Channel) : Card
    data class MovieCard(val movie: Movie) : Card
    data class ActionCard(val title: String, val subtitle: String, val kind: ActionKind) : Card
}

/** Demo actions that don't map to content (e.g. showcase ad formats). */
enum class ActionKind { FULLSCREEN_VIDEO, FULLSCREEN_BANNER }

/** A titled horizontal rail of cards. */
data class Rail(val title: String, val cards: List<Card>)
