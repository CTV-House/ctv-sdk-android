package com.ctvhouse.ctvsdk.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.ctvhouse.ctvsdk.R
import com.ctvhouse.ctvsdk.data.Card

/** Adapter for the cards inside a single horizontal rail. */
class CardsAdapter(
    private val cards: List<Card>,
    private val onClick: (Card) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int = when (cards[position]) {
        is Card.ChannelCard -> TYPE_CHANNEL
        is Card.MovieCard -> TYPE_MOVIE
        is Card.ActionCard -> TYPE_ACTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_CHANNEL -> ChannelVH(inflater.inflate(R.layout.item_card_channel, parent, false))
            TYPE_ACTION -> ActionVH(inflater.inflate(R.layout.item_card_action, parent, false))
            else -> MovieVH(inflater.inflate(R.layout.item_card_movie, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val card = cards[position]) {
            is Card.ChannelCard -> (holder as ChannelVH).bind(card)
            is Card.MovieCard -> (holder as MovieVH).bind(card)
            is Card.ActionCard -> (holder as ActionVH).bind(card)
        }
    }

    override fun getItemCount(): Int = cards.size

    private inner class ChannelVH(view: View) : RecyclerView.ViewHolder(view) {
        private val logo = view.findViewById<ImageView>(R.id.logo)
        private val name = view.findViewById<TextView>(R.id.name)

        init {
            view.enableFocusScale()
        }

        fun bind(card: Card.ChannelCard) {
            val channel = card.channel
            name.text = channel.name
            logo.load(channel.logoUrl) {
                placeholder(R.drawable.tv_banner)
                error(R.drawable.tv_banner)
            }
            itemView.setOnClickListener { onClick(card) }
        }
    }

    private inner class MovieVH(view: View) : RecyclerView.ViewHolder(view) {
        private val poster = view.findViewById<ImageView>(R.id.poster)
        private val title = view.findViewById<TextView>(R.id.title)
        private val subtitle = view.findViewById<TextView>(R.id.subtitle)

        init {
            view.enableFocusScale()
        }

        fun bind(card: Card.MovieCard) {
            val movie = card.movie
            title.text = movie.title
            subtitle.text = "${movie.year} · ${movie.genre}"
            poster.load(movie.posterUrl) {
                placeholder(R.drawable.tv_banner)
                error(R.drawable.tv_banner)
            }
            itemView.setOnClickListener { onClick(card) }
        }
    }

    private inner class ActionVH(view: View) : RecyclerView.ViewHolder(view) {
        private val title = view.findViewById<TextView>(R.id.actionTitle)
        private val subtitle = view.findViewById<TextView>(R.id.actionSubtitle)

        init {
            view.enableFocusScale()
        }

        fun bind(card: Card.ActionCard) {
            title.text = card.title
            subtitle.text = card.subtitle
            itemView.setOnClickListener { onClick(card) }
        }
    }

    private companion object {
        const val TYPE_CHANNEL = 0
        const val TYPE_MOVIE = 1
        const val TYPE_ACTION = 2
    }
}

/** Scales a card up and lifts it when it receives D-pad focus. */
private fun View.enableFocusScale() {
    setOnFocusChangeListener { v, hasFocus ->
        val scale = if (hasFocus) 1.12f else 1f
        v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
        v.translationZ = if (hasFocus) 8f else 0f
    }
}
