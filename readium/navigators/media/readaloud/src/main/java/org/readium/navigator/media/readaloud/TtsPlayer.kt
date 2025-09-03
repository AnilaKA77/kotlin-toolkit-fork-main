/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.navigator.media.readaloud

import kotlin.properties.Delegates
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Error

internal class TtsPlayer<E : Error>(
    engineFactory: (TtsEngine.Listener<E>) -> PausableTtsEngine,
    private val listener: Listener<E>,
) {

    interface Listener<E : Error> {

        fun onItemChanged(player: TtsPlayer<E>, index: Int)

        fun onStateChanged(player: TtsPlayer<E>, state: State)
    }

    internal enum class State {
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
        StateMachine.start(playWhenReady = false)
    ) { property, oldValue, newValue ->
        if (oldValue.playbackState != newValue.playbackState) {
            val state = when (newValue.playbackState) {
                StateMachine.PlaybackState.Ended -> State.Ended
                StateMachine.PlaybackState.Ready -> State.Ready
                StateMachine.PlaybackState.Starved -> State.Starved
            }
            listener.onStateChanged(this, state)
        }

        if (oldValue.index != newValue.index) {
            listener.onItemChanged(this, newValue.index)
        }
    }

    var playWhenReady: Boolean
        get() = state.playWhenReady
        set(value) {
            with(stateMachine) {
                state = if (value) state.resume() else state.pause()
            }
        }

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
        val index: Int,
    )

    companion object {

        fun start(playWhenReady: Boolean): State {
            return State(
                playWhenReady = playWhenReady,
                playbackState = PlaybackState.Starved,
                index = 0
            )
        }
    }

    fun State.pause(): State {
        engine.pause()
        return copy(playWhenReady = false)
    }

    fun State.resume(): State {
        engine.resume()
        return copy(playWhenReady = true)
    }

    fun State.seekTo(index: Int): State {
        if (playWhenReady) {
            engine.stop()
            engine.speak(index)
        }
        return copy(index = index)
    }

    fun State.onUtteranceCompleted(): State {
        if (index < engine.utterances.size - 1) {
            engine.speak(index + 1)
            return copy(index = index + 1)
        } else {
            return copy(playbackState = PlaybackState.Ended)
        }
    }

    fun State.onEngineReady(): State {
        if (playWhenReady) {
            engine.speak(index)
        }
        return copy(playbackState = PlaybackState.Ready)
    }
}
