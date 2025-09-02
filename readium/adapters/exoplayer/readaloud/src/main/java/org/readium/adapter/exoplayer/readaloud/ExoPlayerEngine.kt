/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.adapter.exoplayer.readaloud

import android.app.Application
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.readium.navigator.media.readaloud.AudioEngine
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.extensions.findInstance
import org.readium.r2.shared.util.ThrowableError
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.data.ReadException

/**
 * An [AudioEngine] based on Media3 ExoPlayer.
 */
@ExperimentalReadiumApi
@OptIn(ExperimentalCoroutinesApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
public class ExoPlayerEngine private constructor(
    private val exoPlayer: ExoPlayer,
    private val listener: AudioEngine.Listener,
) : AudioEngine {

    public companion object {

        public operator fun invoke(
            application: Application,
            dataSourceFactory: DataSource.Factory,
            listener: AudioEngine.Listener,
        ): ExoPlayerEngine {
            val exoPlayer = ExoPlayer.Builder(application)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .build()

            exoPlayer.preloadConfiguration = ExoPlayer.PreloadConfiguration(10_000_000L)

            return ExoPlayerEngine(exoPlayer, listener)
        }
    }

    private inner class Listener : Player.Listener {

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                val newState = when (player.playbackState) {
                    Player.STATE_READY -> AudioEngine.State.Ready
                    Player.STATE_BUFFERING -> AudioEngine.State.Starved
                    Player.STATE_ENDED -> AudioEngine.State.Ended
                    else -> null
                }
                newState?.let { this@ExoPlayerEngine.listener.onStateChanged(this@ExoPlayerEngine, it) }
            }

            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                this@ExoPlayerEngine.listener.onItemChanged(this@ExoPlayerEngine, player.currentMediaItemIndex)
            }
        }
    }

    public sealed class Error(
        override val message: String,
        override val cause: org.readium.r2.shared.util.Error?,
    ) : org.readium.r2.shared.util.Error {

        public data class Engine(override val cause: ThrowableError<ExoPlaybackException>) :
            Error("An error occurred in the ExoPlayer engine.", cause)

        public data class Source(override val cause: ReadError) :
            Error("An error occurred while trying to read publication content.", cause)
    }

    private val coroutineScope: CoroutineScope =
        MainScope()

    private var prepareCalled: Boolean = false

    init {
        exoPlayer.addListener(Listener())
    }

    override fun setPlaylist(
        items: List<AudioEngine.Item>,
    ) {
        val mediaItems = items.map { item ->
            val clippingConfig = MediaItem.ClippingConfiguration.Builder()
                .apply {
                    item.interval?.start?.let { setStartPositionMs(it.inWholeMilliseconds) }
                    item.interval?.end?.let { setEndPositionMs(it.inWholeMilliseconds) }
                }.build()
            MediaItem.Builder()
                .setUri(item.href.toString())
                .setClippingConfiguration(clippingConfig)
                .build()
        }
        exoPlayer.setMediaItems(mediaItems)

        if (!prepareCalled) {
            exoPlayer.prepare()
            prepareCalled = true
        }
    }

    override fun seekTo(index: Int) {
        exoPlayer.seekTo(index, 0L)
    }

    override var playWhenReady: Boolean
        get() = exoPlayer.playWhenReady
        set(value) {
            exoPlayer.playWhenReady = value
        }

    public fun close() {
        coroutineScope.cancel()
        exoPlayer.release()
    }

    @OptIn(InternalReadiumApi::class)
    private fun ExoPlaybackException.toError(): Error {
        val readError =
            if (type == ExoPlaybackException.TYPE_SOURCE) {
                sourceException.findInstance(ReadException::class.java)?.error
            } else {
                null
            }

        return if (readError == null) {
            Error.Engine(ThrowableError(this))
        } else {
            Error.Source(readError)
        }
    }
}
