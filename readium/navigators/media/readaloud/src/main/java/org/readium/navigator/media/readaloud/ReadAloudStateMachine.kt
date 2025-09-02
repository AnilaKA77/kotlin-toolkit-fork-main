/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.navigator.media.readaloud

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Error

internal class ReadAloudStateMachine(
    private val dataLoader: ReadAloudDataLoader,
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
        val segment: ReadAloudSegment,
        val settings: ReadAloudSettings,
    )

    sealed interface Event {

        data class AudioEngineStateChanged(
            val engine: AudioEngine,
            val state: AudioEngine.State,
        ) : Event

        data class AudioEngineItemChanged(
            val engine: AudioEngine,
            val index: Int,
        ) : Event
    }

    fun play(segment: ReadAloudSegment, playWhenReady: Boolean, settings: ReadAloudSettings): State {
        segment.engine.playWhenReady = playWhenReady

        return State(
            playbackState = PlaybackState.Starved,
            playWhenReady = playWhenReady,
            node = segment.nodes[0],
            segment = segment,
            settings = settings
        )
    }

    fun State.pause(): State {
        segment.engine.playWhenReady = false
        return copy(playWhenReady = false)
    }

    fun State.resume(): State {
        segment.engine.playWhenReady = true
        return copy(playWhenReady = true)
    }

    fun State.jump(node: ReadAloudNode): State {
        val firstContentNode = with(navigationHelper) { node.firstContentNode() }
            ?: return copy(playbackState = PlaybackState.Ended)

        val itemRef = dataLoader.getItemRef(firstContentNode)
            ?: return copy(playbackState = PlaybackState.Ended)

        val engineFood = itemRef.segment

        when (engineFood) {
            is AudioSegment -> engineFood.engine.seekTo(itemRef.nodeIndex!!)
            is TtsSegment -> TODO()
        }

        return play(engineFood, playWhenReady, settings)
    }

    fun State.updateSettings(
        oldSettings: ReadAloudSettings,
        newSettings: ReadAloudSettings,
    ): State {
        navigationHelper.settings = newSettings
        dataLoader.settings = newSettings
        return copy(settings = newSettings)
    }

    fun State.onEvent(event: Event): State = when (event) {
        is Event.AudioEngineStateChanged ->
            onAudioEngineStateChanged(event.engine, event.state)
        is Event.AudioEngineItemChanged ->
            onAudioEngineItemChanged(event.engine, event.index)
    }

    fun State.onAudioEngineStateChanged(engine: AudioEngine, audioEngineState: AudioEngine.State): State {
        val currentEngine = (segment as? AudioSegment)?.engine
        if (currentEngine != engine) {
            return this
        }

        return when (audioEngineState) {
            AudioEngine.State.Ready -> copy(playbackState = PlaybackState.Ready)
            AudioEngine.State.Starved -> copy(playbackState = PlaybackState.Starved)
            AudioEngine.State.Ended -> onAudioEngineEnded()
        }
    }

    private fun State.onAudioEngineEnded(): State {
        val nextNode = node.next()
            ?: return copy(playbackState = PlaybackState.Ended)

        val newSegment = dataLoader.getItemRef(nextNode)?.segment
            ?: return copy(playbackState = PlaybackState.Ended)

        newSegment.engine.playWhenReady = playWhenReady

        return copy(segment = newSegment, playbackState = PlaybackState.Starved, node = newSegment.nodes[0])
    }

    fun State.onAudioEngineItemChanged(engine: AudioEngine, item: Int): State {
        val currentEngine = (segment as? AudioSegment)?.engine
        if (currentEngine != engine) {
            return this
        }

        dataLoader.onPlaybackProgressed(segment.nodes[item])
        return copy(node = segment.nodes[item])
    }
}
