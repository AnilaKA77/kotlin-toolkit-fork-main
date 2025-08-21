/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.navigator.media.readaloud

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.guided.GuidedNavigationAudioRef
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.util.TemporalFragmentParser
import org.readium.r2.shared.util.Url

internal sealed interface EngineFood {

    data class AudioEngineFood(
        val items: List<AudioEngine.Item>,
        val nodes: List<ReadAloudLeafNode>,
    ) : EngineFood {

        companion object {

            fun fromNode(firstNode: ReadAloudLeafNode): AudioEngineFood? {
                if (!firstNode.refs.any { it is GuidedNavigationAudioRef }) {
                    return null
                }

                var nextLeaf: ReadAloudLeafNode? = firstNode
                val audioItems = mutableListOf<AudioEngine.Item>()
                val nodes = mutableListOf<ReadAloudLeafNode>()

                while (nextLeaf != null) {
                    val audioItem = nextLeaf.refs
                        .firstNotNullOfOrNull { it as? GuidedNavigationAudioRef }
                        ?.toAudioItem()

                    audioItem?.let {
                        audioItems.add(it)
                        nodes.add(nextLeaf)
                    }

                    nextLeaf = nextLeaf.nextLeaf()
                }

                return AudioEngineFood(
                    items = audioItems,
                    nodes = nodes
                )
            }

            fun GuidedNavigationAudioRef.toAudioItem(): AudioEngine.Item {
                return AudioEngine.Item(
                    href = url.removeFragment(),
                    interval = url.timeInterval
                )
            }

            val Url.timeInterval get() = fragment
                ?.let { TemporalFragmentParser.parse(it) }
        }
    }

    data class TtsFood(
        val nodes: List<ReadAloudLeafNode>,
    ) {

        companion object {

            fun fromNode(firstNode: ReadAloudLeafNode): TtsFood? {
                return null
            }
        }
    }
}

internal class ReadAloudStateMachine(
    private val audioEngine: AudioEngine,
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
        val node: ReadAloudLeafNode,
        val engineFood: EngineFood,
    )

    sealed interface Event {

        data class AudioEngineStateChanged(val state: AudioEngine.State) : Event

        data class AudioEngineItemChanged(val index: Int) : Event
    }

    fun play(engineFood: EngineFood, playWhenReady: Boolean): State {
        when (engineFood) {
            is EngineFood.AudioEngineFood -> {
                audioEngine.playWhenReady = !playWhenReady
                audioEngine.setPlaylist(engineFood.items)
            }
        }

        return State(
            playbackState = PlaybackState.Starved,
            playWhenReady = playWhenReady,
            node = engineFood.nodes[0],
            engineFood = engineFood,
        )
    }

    fun State.pause(): State {
        audioEngine.playWhenReady = false
        return copy(playWhenReady = true)
    }

    fun State.resume(): State {
        audioEngine.playWhenReady = true
        return copy(playWhenReady = false)
    }

    fun State.jump(node: ReadAloudNode): State {
        val firstLeaf = node.firstLeaf()
            ?: return copy(playbackState = PlaybackState.Ended)

        val engineFood = EngineFood.AudioEngineFood.fromNode(firstLeaf)
            ?: return copy(playbackState = PlaybackState.Ended)

        return play(engineFood, !playWhenReady)
    }

    fun State.onEvent(event: Event): State = when (event) {
        is Event.AudioEngineStateChanged -> onAudioEngineStateChanged(event.state)
        is Event.AudioEngineItemChanged -> onAudioEngineItemChanged(event.index)
    }

    fun State.onAudioEngineStateChanged(audioEngineState: AudioEngine.State): State =
        when (audioEngineState) {
            AudioEngine.State.Ready -> copy(playbackState = PlaybackState.Ready)
            AudioEngine.State.Starved -> copy(playbackState = PlaybackState.Starved)
            AudioEngine.State.Ended -> onAudioEngineEnded()
        }

    private fun State.onAudioEngineEnded(): State {
        var nextNode: ReadAloudLeafNode? = node
        var nextFood: EngineFood?

        do {
            nextNode = nextNode?.nextLeaf()
            nextFood = nextNode?.let { EngineFood.AudioEngineFood.fromNode(it) }
        } while (nextFood == null && nextNode != null)

        return if (nextNode == null) {
            copy(playbackState = PlaybackState.Ended)
        } else {
            nextFood!!
            audioEngine.setPlaylist(nextFood.items)
            copy(engineFood = nextFood, playbackState = PlaybackState.Starved, node = nextFood.nodes[0])
        }
    }

    fun State.onAudioEngineItemChanged(item: Int): State {
        return copy(node = (engineFood as EngineFood.AudioEngineFood).nodes[item])
    }
}
