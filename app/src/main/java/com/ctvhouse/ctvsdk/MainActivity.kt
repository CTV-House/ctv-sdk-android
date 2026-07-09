package com.ctvhouse.ctvsdk

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.ctvhouse.ctvads.AdCallbacks
import com.ctvhouse.ctvads.AdFormat
import com.ctvhouse.ctvads.AdSize
import com.ctvhouse.ctvads.AdTrackingEvent
import com.ctvhouse.ctvads.InterstitialAd
import com.ctvhouse.ctvads.openrtb.Bid
import com.ctvhouse.ctvsdk.data.ActionKind
import com.ctvhouse.ctvsdk.data.Card
import com.ctvhouse.ctvsdk.data.DemoRepository
import com.ctvhouse.ctvsdk.databinding.ActivityMainBinding
import com.ctvhouse.ctvsdk.ui.RailsAdapter

class MainActivity : FragmentActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rails.setNumColumns(1)
        val rails = DemoRepository(this).rails()
        binding.rails.adapter = RailsAdapter(rails, ::onCardClick)
    }

    private fun onCardClick(card: Card) {
        when (card) {
            is Card.ChannelCard -> startActivity(
                Intent(this, LiveTvActivity::class.java).apply {
                    putExtra(LiveTvActivity.EXTRA_NAME, card.channel.name)
                    putExtra(LiveTvActivity.EXTRA_STREAM_URL, card.channel.streamUrl)
                }
            )

            is Card.MovieCard -> startActivity(
                Intent(this, MoviePlayerActivity::class.java).apply {
                    putExtra(MoviePlayerActivity.EXTRA_TITLE, card.movie.title)
                    putExtra(MoviePlayerActivity.EXTRA_VIDEO_URL, card.movie.videoUrl)
                }
            )

            is Card.ActionCard -> showInterstitial(
                when (card.kind) {
                    ActionKind.FULLSCREEN_VIDEO -> AdFormat.VIDEO
                    ActionKind.FULLSCREEN_BANNER -> AdFormat.BANNER
                }
            )
        }
    }

    private fun showInterstitial(format: AdFormat) {
        val ad = InterstitialAd(this)
        ad.setPlacementId("interstitial")
        ad.addListener(AdCallbacks(
            onFailed = { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
            },
            onLoaded = { bid: Bid ->
                Toast.makeText(this, "onLoaded " + bid.id, Toast.LENGTH_SHORT).show()
                ad.show()
            },
            onRendered = {bid: Bid ->
                Toast.makeText(this, "onRendered " + bid.id, Toast.LENGTH_SHORT).show()
            },
            onClicked = {bid: Bid ->
                Toast.makeText(this, "onClicked "  + bid.id, Toast.LENGTH_SHORT).show()
            },
            onTracking = {track: AdTrackingEvent ->
                Toast.makeText(this, track.name, Toast.LENGTH_SHORT).show()
            }
        ),)
        ad.load(lifecycleScope, format, AdSize(800, 600),0.0, false)
    }
}
