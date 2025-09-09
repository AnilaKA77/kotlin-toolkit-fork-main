/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.navigator.media.readaloud

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Error

internal sealed interface SegmentPlayer {

    enum class PlaybackState {
        Ready,
        Starved,
        Ended,
    }

    val player: Any

    var playWhenReady: Boolean

    var pitch: Double

    var speed: Double

    val playbackState: PlaybackState

    fun prepare()

    fun seekTo(index: Int)

    fun release()
}

internal class TtsSegmentPlayer<E : Error>(
    override val player: TtsPlayer<E>,
) : SegmentPlayer {

    override var playWhenReady: Boolean
        get() = player.playWhenReady
        set(value) {
            player.playWhenReady = value
        }
    override var pitch: Double
        get() = player.pitch
        set(value) {
            player.pitch = value
        }

    override var speed: Double
        get() = player.speed
        set(value) {
            player.speed = value
        }

    override val playbackState: SegmentPlayer.PlaybackState
        get() = when (player.playbackState) {
            TtsPlayer.PlaybackState.Ready -> SegmentPlayer.PlaybackState.Ready
            TtsPlayer.PlaybackState.Starved -> SegmentPlayer.PlaybackState.Starved
            TtsPlayer.PlaybackState.Ended -> SegmentPlayer.PlaybackState.Ended
        }

    override fun prepare() {
        player.prepare()
    }

    override fun seekTo(index: Int) {
        player.seekTo(index)
    }

    override fun release() {
        player.release()
    }
}

internal class AudioSegmentPlayer(
    override val player: AudioEngine,
) : SegmentPlayer {

    override var playWhenReady: Boolean
        get() = player.playWhenReady
        set(value) {
            player.playWhenReady = value
        }

    override var pitch: Double
        get() = player.pitch
        set(value) {
            player.pitch = value
        }

    override var speed: Double
        get() = player.speed
        set(value) {
            player.speed = value
        }

    override val playbackState: SegmentPlayer.PlaybackState
        get() = when (player.playbackState) {
            AudioEngine.PlaybackState.Ready -> SegmentPlayer.PlaybackState.Ready
            AudioEngine.PlaybackState.Starved -> SegmentPlayer.PlaybackState.Starved
            AudioEngine.PlaybackState.Ended -> SegmentPlayer.PlaybackState.Ended
        }

    override fun prepare() {
    }

    override fun seekTo(index: Int) {
        player.seekTo(index)
    }

    override fun release() {
        player.release()
    }
}
