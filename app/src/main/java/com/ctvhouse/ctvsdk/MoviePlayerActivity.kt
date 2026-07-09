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
import com.ctvhouse.ctvsdk.databinding.ActivityMoviePlayerBinding

/**
 * Movie playback: shows a pre-roll video ad (from the SDK), then plays the
 * static demo content video full-screen.
 */
class MoviePlayerActivity : FragmentActivity() {

    private lateinit var binding: ActivityMoviePlayerBinding
    private var player: ExoPlayer? = null
    private var contentStarted = false

    private val videoUrl: String by lazy { intent.getStringExtra(EXTRA_VIDEO_URL).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMoviePlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.preroll.listener = object : AdListener {
            override fun onAdFailed(error: AdError) = startContent()
            override fun onAdCompleted(bid: Bid) = startContent()
        }
    }

    override fun onStart() {
        super.onStart()
        if (contentStarted) {
            resumeContent()
        } else {
            // Pre-roll ad first; content starts when the ad finishes or fails.
            binding.preroll.loadAd(lifecycleScope, AdSize(1920, 1080))
        }
    }

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    private fun startContent() {
        if (contentStarted || videoUrl.isBlank()) return
        contentStarted = true
        binding.preroll.visibility = View.GONE
        binding.contentPlayer.visibility = View.VISIBLE

        val exo = ExoPlayer.Builder(this).build()
        player = exo
        binding.contentPlayer.player = exo
        binding.contentPlayer.useController = true
        exo.setMediaItem(MediaItem.fromUri(videoUrl))
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun resumeContent() {
        player?.playWhenReady = true
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        binding.contentPlayer.player = null
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_VIDEO_URL = "extra_video_url"
    }
}
