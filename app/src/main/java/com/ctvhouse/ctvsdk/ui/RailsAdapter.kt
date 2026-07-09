package com.ctvhouse.ctvsdk.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.HorizontalGridView
import androidx.recyclerview.widget.RecyclerView
import com.ctvhouse.ctvsdk.R
import com.ctvhouse.ctvsdk.data.Card
import com.ctvhouse.ctvsdk.data.Rail

/** Vertical list of titled horizontal rails. */
class RailsAdapter(
    private val rails: List<Rail>,
    private val onCardClick: (Card) -> Unit,
) : RecyclerView.Adapter<RailsAdapter.RailVH>() {

    private val cardsPool = RecyclerView.RecycledViewPool()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RailVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rail, parent, false)
        return RailVH(view)
    }

    override fun onBindViewHolder(holder: RailVH, position: Int) {
        holder.bind(rails[position])
    }

    override fun getItemCount(): Int = rails.size

    inner class RailVH(view: View) : RecyclerView.ViewHolder(view) {
        private val title = view.findViewById<TextView>(R.id.railTitle)
        private val cards = view.findViewById<HorizontalGridView>(R.id.railCards)

        init {
            cards.setNumRows(1)
            cards.setRecycledViewPool(cardsPool)
        }

        fun bind(rail: Rail) {
            title.text = rail.title
            val isMovieRail = rail.cards.firstOrNull() is Card.MovieCard
            val heightDp = if (isMovieRail) 380 else 280
            cards.layoutParams = cards.layoutParams.apply {
                height = (heightDp * cards.resources.displayMetrics.density).toInt()
            }
            cards.adapter = CardsAdapter(rail.cards, onCardClick)
        }
    }
}
