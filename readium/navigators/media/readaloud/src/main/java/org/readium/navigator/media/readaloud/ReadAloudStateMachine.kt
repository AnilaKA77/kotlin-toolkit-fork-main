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

            fun fromNode(node: ReadAloudNode): AudioEngineFood? {
                val firstNode = node.firstLeaf() ?: return null
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
}

internal class ReadAloudStateMachine(
    private val audioEngine: AudioEngine,
) {
    sealed interface State {

        data class Playing(
            val paused: Boolean,
            val node: ReadAloudLeafNode,
            val engineFood: EngineFood,
        ) : State

        data object Ended : State

        data class Failure(val error: Error) : State
    }

    sealed interface Event {

        data object AudioEngineEnded : Event
    }

    fun start(initialNode: ReadAloudNode, paused: Boolean): State {
        val firstLeaf = initialNode.nextLeaf()
            ?: return State.Ended
        val engineFood = EngineFood.AudioEngineFood.fromNode(firstLeaf)
            ?: return State.Ended

        if (paused) audioEngine.pause() else audioEngine.resume()
        audioEngine.setPlaylist(engineFood.items)
        return State.Playing(paused = paused, node = firstLeaf, engineFood = engineFood)
    }

    fun State.pause(): State =
        when (this) {
            State.Ended -> this
            is State.Failure -> this
            is State.Playing -> {
                if (engineFood is EngineFood.AudioEngineFood) {
                    audioEngine.pause()
                }
                copy(paused = true)
            }
        }

    fun State.resume(): State =
        when (this) {
            State.Ended -> this
            is State.Failure -> this
            is State.Playing -> {
                if (engineFood is EngineFood.AudioEngineFood) {
                    audioEngine.resume()
                }
                copy(paused = false)
            }
        }

    fun State.jump(node: ReadAloudNode): State =
        when (this) {
            State.Ended -> start(node, paused = true)
            is State.Failure -> this
            is State.Playing -> start(node, paused = paused)
        }

    fun State.onEvent(event: Event): State = when (event) {
        Event.AudioEngineEnded -> onAudioEngineEnded()
    }

    fun State.onAudioEngineEnded(): State =
        when (this) {
            State.Ended -> this
            is State.Failure -> this
            is State.Playing -> State.Ended
        }
}
