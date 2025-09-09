/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.navigator.media.readaloud

import kotlin.properties.Delegates
import org.readium.navigator.media.readaloud.StateMachine.PlaybackState
import org.readium.navigator.media.readaloud.StateMachine.State
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Error

internal class TtsPlayer<E : Error>(
    engineFactory: (TtsEngine.Listener<E>) -> PausableTtsEngine,
    private val listener: Listener<E>,
) {

    interface Listener<E : Error> {

        fun onItemChanged(player: TtsPlayer<E>, index: Int)

        fun onStateChanged(player: TtsPlayer<E>, state: TtsPlayer.PlaybackState)
    }

    internal enum class PlaybackState {
        Ready,
        Starved,
        Ended,
    }

    private inner class EngineListener : TtsEngine.Listener<E> {
        override fun onReady() {
            with(stateMachine) {
                state = state.onEngineReady()
            }
        }

        override fun onDone() {
            with(stateMachine) {
                state = state.onUtteranceCompleted()
            }
        }

        override fun onInterrupted() {
        }

        override fun onRange(range: IntRange) {
        }

        override fun onError(error: E) {
        }
    }

    private val ttsEngine = engineFactory(EngineListener())

    private val stateMachine = StateMachine<E>(ttsEngine)

    private var state by Delegates.observable(
        State(
            playWhenReady = false,
            playbackState = StateMachine.PlaybackState.Starved,
            indexToPlay = 0,
            lastSubmittedIndex = null
        )
    ) { property, oldValue, newValue ->
        if (oldValue.playbackState != newValue.playbackState) {
            val playerState = newValue.playbackState.toPlayerPlaybackState()
            listener.onStateChanged(this, playerState)
        }

        if (oldValue.indexToPlay != newValue.indexToPlay) {
            listener.onItemChanged(this, newValue.indexToPlay)
        }
    }

    var playWhenReady: Boolean
        get() = state.playWhenReady
        set(value) {
            with(stateMachine) {
                state = if (value) state.resume() else state.pause()
            }
        }

    var pitch: Double
        get() = ttsEngine.pitch
        set(value) {
            ttsEngine.pitch = value
        }

    var speed: Double
        get() = ttsEngine.speed
        set(value) {
            ttsEngine.speed = value
        }

    val playbackState: TtsPlayer.PlaybackState get() =
        state.playbackState.toPlayerPlaybackState()

    fun prepare() {
        ttsEngine.prepare()
    }

    fun seekTo(index: Int) {
        with(stateMachine) {
            state = state.seekTo(index)
        }
    }

    fun release() {
        ttsEngine.release()
    }
}

private fun StateMachine.PlaybackState.toPlayerPlaybackState() =
    when (this) {
        PlaybackState.Ended -> TtsPlayer.PlaybackState.Ended
        PlaybackState.Ready -> TtsPlayer.PlaybackState.Ready
        PlaybackState.Starved -> TtsPlayer.PlaybackState.Starved
    }

private class StateMachine<E : Error>(
    private val engine: PausableTtsEngine,
) {
    sealed interface PlaybackState {

        data object Ready : PlaybackState

        data object Starved : PlaybackState

        data object Ended : PlaybackState
    }

    data class State(
        val playbackState: PlaybackState,
        val playWhenReady: Boolean,
        val indexToPlay: Int,
        val lastSubmittedIndex: Int?,
    )

    fun State.pause(): State {
        engine.pause()
        return copy(playWhenReady = false)
    }

    fun State.resume(): State {
        if (lastSubmittedIndex == indexToPlay) {
            engine.resume()
        } else {
            engine.stop()
            engine.speak(indexToPlay)
        }

        return copy(playWhenReady = true, lastSubmittedIndex = indexToPlay)
    }

    fun State.seekTo(index: Int): State {
        if (playWhenReady) {
            engine.stop()
            engine.speak(index)
            return copy(indexToPlay = index, lastSubmittedIndex = index)
        } else {
            return copy(indexToPlay = index)
        }
    }

    fun State.onUtteranceCompleted(): State {
        if (indexToPlay < engine.utterances.size - 1) {
            val newIndexToPlay = indexToPlay + 1
            engine.speak(newIndexToPlay)
            return copy(indexToPlay = newIndexToPlay, lastSubmittedIndex = newIndexToPlay)
        } else {
            return copy(playbackState = PlaybackState.Ended)
        }
    }

    fun State.onEngineReady(): State {
        if (playWhenReady) {
            engine.speak(indexToPlay)
        }
        return copy(playbackState = PlaybackState.Ready, lastSubmittedIndex = indexToPlay)
    }
}
