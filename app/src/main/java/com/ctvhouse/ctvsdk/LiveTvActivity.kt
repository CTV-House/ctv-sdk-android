package com.ctvhouse.ctvsdk

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.ctvhouse.ctvads.AdError
import com.ctvhouse.ctvads.AdListener
import com.ctvhouse.ctvads.AdSize
import com.ctvhouse.ctvads.openrtb.Bid
import com.ctvhouse.ctvsdk.databinding.ActivityLiveTvBinding

/**
 * Live TV: plays the selected channel's HLS stream full-screen with a 320×50
 * banner ad slot overlaid on top of the video.
 */
class LiveTvActivity : FragmentActivity() {

    private lateinit var binding: ActivityLiveTvBinding
    private var player: ExoPlayer? = null

    private val streamUrl: String by lazy {
        intent.getStringExtra(EXTRA_STREAM_URL).orEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveTvBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.overlayAd.listener = object : AdListener {
            override fun onAdRendered(bid: Bid) {
                binding.overlayAd.visibility = View.VISIBLE
            }

            override fun onAdFailed(error: AdError) {
                binding.overlayAd.visibility = View.GONE
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startStream()
        // Request the overlay banner once the screen is visible.
        // fixedSize keeps the slot pinned to 320×50 even if the bidder returns
        // a creative with different dimensions.
        binding.overlayAd.loadAd(lifecycleScope, AdSize.BANNER, fixedSize = true)
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun startStream() {
        if (streamUrl.isBlank()) return
        releasePlayer()
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        binding.player.player = exo
        binding.player.useController = false
        exo.setMediaItem(MediaItem.fromUri(streamUrl))
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        binding.player.player = null
    }

    companion object {
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_STREAM_URL = "extra_stream_url"
    }
}
