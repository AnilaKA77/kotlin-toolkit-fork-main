/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.navigator.media.readaloud

import org.readium.navigator.media.readaloud.preferences.ReadAloudSettings
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

    fun init(
        segment: ReadAloudSegment,
        itemIndex: Int,
        playWhenReady: Boolean,
        settings: ReadAloudSettings,
    ): State {
        segment.player.seekTo(itemIndex)
        segment.player.playWhenReady = playWhenReady
        segment.player.speed = settings.speed
        segment.player.pitch = settings.pitch

        return State(
            playbackState = segment.player.playbackState.toStateMachinePlaybackState(),
            playWhenReady = playWhenReady,
            node = segment.nodes[itemIndex],
            index = itemIndex,
            segment = segment,
            settings = settings
        )
    }

    fun State.jump(node: ReadAloudNode): State {
        val firstContentNode = with(navigationHelper) { node.firstContentNode() }
            ?: return copy(playbackState = PlaybackState.Ended)

        val itemRef = dataLoader.getItemRef(firstContentNode)
            ?: return copy(playbackState = PlaybackState.Ended)

        val currentSegment = segment

        if (currentSegment != itemRef.segment) {
            currentSegment.player.release()
        }

        val index = itemRef.nodeIndex!! // This is a content node
        return init(itemRef.segment, index, playWhenReady, settings)
    }

    fun State.restart(): State {
        segment.player.release()

        val itemRef = dataLoader.getItemRef(node)
            ?: return copy(playbackState = PlaybackState.Ended)

        return init(itemRef.segment, index, playWhenReady, settings)
    }

    fun State.pause(): State {
        segment.player.playWhenReady = false
        return copy(playWhenReady = false)
    }

    fun State.resume(): State {
        segment.player.playWhenReady = true
        return copy(playWhenReady = true)
    }

    fun State.updateSettings(
        oldSettings: ReadAloudSettings,
        newSettings: ReadAloudSettings,
    ): State {
        navigationHelper.settings = newSettings
        dataLoader.settings = newSettings

        segment.player.pitch = settings.pitch
        segment.player.speed = settings.speed

        return copy(settings = newSettings).restart()
    }

    fun State.onAudioEngineStateChanged(audioEngineState: AudioEngine.PlaybackState): State {
        return when (audioEngineState) {
            AudioEngine.PlaybackState.Ready -> copy(playbackState = PlaybackState.Ready)
            AudioEngine.PlaybackState.Starved -> copy(playbackState = PlaybackState.Starved)
            AudioEngine.PlaybackState.Ended -> onSegmentPlaybackEnded()
        }
    }

    fun State.onTtsPlayerStateChanged(state: TtsPlayer.PlaybackState): State {
        return when (state) {
            TtsPlayer.PlaybackState.Ready -> copy(playbackState = PlaybackState.Ready)
            TtsPlayer.PlaybackState.Starved -> copy(playbackState = PlaybackState.Starved)
            TtsPlayer.PlaybackState.Ended -> onSegmentPlaybackEnded()
        }
    }

    fun State.onAudioEngineItemChanged(item: Int): State {
        dataLoader.onPlaybackProgressed(segment.nodes[item])
        return copy(node = segment.nodes[item], index = item)
    }

    fun State.onTtsPlayerItemChanged(item: Int): State {
        dataLoader.onPlaybackProgressed(segment.nodes[item])
        return copy(node = segment.nodes[item], index = item)
    }

    private fun State.onSegmentPlaybackEnded(): State {
        val nextNode = node.next()
            ?: return copy(playbackState = PlaybackState.Ended)

        val newSegment = dataLoader.getItemRef(nextNode)?.segment
            ?: return copy(playbackState = PlaybackState.Ended)

        segment.player.release()

        newSegment.player.playWhenReady = playWhenReady
        newSegment.player.speed = settings.speed
        newSegment.player.pitch = settings.pitch

        return copy(
            segment = newSegment,
            playbackState = segment.player.playbackState.toStateMachinePlaybackState(),
            node = newSegment.nodes[0],
            index = 0
        )
    }
}

private fun SegmentPlayer.PlaybackState.toStateMachinePlaybackState() =
    when (this) {
        SegmentPlayer.PlaybackState.Ready -> ReadAloudStateMachine.PlaybackState.Ready
        SegmentPlayer.PlaybackState.Starved -> ReadAloudStateMachine.PlaybackState.Starved
        SegmentPlayer.PlaybackState.Ended -> ReadAloudStateMachine.PlaybackState.Ended
    }
