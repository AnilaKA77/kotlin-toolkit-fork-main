/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.navigator.media.readaloud

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Error

internal class ReadAloudStateMachine<E : Error>(
    private val dataLoader: ReadAloudDataLoader<E>,
    private val navigationHelper: ReadAloudNavigationHelper,
) {

    sealed interface PlaybackState {

        data object Ready : PlaybackState

        data object Starved : PlaybackState

        data object Ended : PlaybackState

        data class Failure(val error: Error) : PlaybackState
    }

    data class State(
        val playbackState: PlaybackState,
        val playWhenReady: Boolean,
        val node: ReadAloudNode,
        val index: Int,
        val segment: ReadAloudSegment,
        val settings: ReadAloudSettings,
    )

    fun play(
        segment: ReadAloudSegment,
        index: Int,
        playWhenReady: Boolean,
        settings: ReadAloudSettings,
    ): State {
        segment.player.seekTo(index)

        segment.player.playWhenReady = playWhenReady

        return State(
            playbackState = PlaybackState.Starved,
            playWhenReady = playWhenReady,
            node = segment.nodes[index],
            index = index,
            segment = segment,
            settings = settings
        )
    }

    fun State.pause(): State {
        segment.player.playWhenReady = false
        return copy(playWhenReady = false)
    }

    fun State.resume(): State {
        segment.player.playWhenReady = true
        return copy(playWhenReady = true)
    }

    fun State.jump(node: ReadAloudNode): State {
        val firstContentNode = with(navigationHelper) { node.firstContentNode() }
            ?: return copy(playbackState = PlaybackState.Ended)

        val itemRef = dataLoader.getItemRef(firstContentNode)
            ?: return copy(playbackState = PlaybackState.Ended)

        val index = itemRef.nodeIndex!!

        return play(itemRef.segment, index, playWhenReady, settings)
    }

    fun State.updateSettings(
        oldSettings: ReadAloudSettings,
        newSettings: ReadAloudSettings,
    ): State {
        navigationHelper.settings = newSettings
        dataLoader.settings = newSettings
        return copy(settings = newSettings)
    }

    fun State.onAudioEngineStateChanged(audioEngineState: AudioEngine.State): State {
        return when (audioEngineState) {
            AudioEngine.State.Ready -> copy(playbackState = PlaybackState.Ready)
            AudioEngine.State.Starved -> copy(playbackState = PlaybackState.Starved)
            AudioEngine.State.Ended -> onSegmentPlaybackEnded()
        }
    }

    fun State.onAudioEngineItemChanged(item: Int): State {
        dataLoader.onPlaybackProgressed(segment.nodes[item])
        return copy(node = segment.nodes[item], index = item)
    }

    fun State.onTtsPlayerStateChanged(state: TtsPlayer.State): State {
        return when (state) {
            TtsPlayer.State.Ready -> copy(playbackState = PlaybackState.Ready)
            TtsPlayer.State.Starved -> copy(playbackState = PlaybackState.Starved)
            TtsPlayer.State.Ended -> onSegmentPlaybackEnded()
        }
    }

    fun State.onTtsPlayerItemChanged(item: Int): State {
        dataLoader.onPlaybackProgressed(segment.nodes[item])
        return copy(node = segment.nodes[item], index = item)
    }

    private fun State.onSegmentPlaybackEnded(): State {
        segment.player.release()

        val nextNode = node.next()
            ?: return copy(playbackState = PlaybackState.Ended)

        val newSegment = dataLoader.getItemRef(nextNode)?.segment
            ?: return copy(playbackState = PlaybackState.Ended)

        newSegment.player.playWhenReady = playWhenReady

        return copy(
            segment = newSegment,
            playbackState = PlaybackState.Starved,
            node = newSegment.nodes[0],
            index = 0
        )
    }
}
