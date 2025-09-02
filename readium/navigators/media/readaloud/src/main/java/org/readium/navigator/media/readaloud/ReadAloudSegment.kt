/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.navigator.media.readaloud

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.guided.GuidedNavigationAudioRef
import org.readium.r2.shared.guided.GuidedNavigationText
import org.readium.r2.shared.util.TemporalFragmentParser
import org.readium.r2.shared.util.TimeInterval
import org.readium.r2.shared.util.Url

internal sealed interface ReadAloudSegment {

    val nodes: List<ReadAloudNode>

    val engine: ReadAloudEngine

    val emptyNodes: Set<ReadAloudNode>
}

internal data class AudioSegment(
    override val engine: AudioEngine,
    val items: List<AudioEngine.Item>,
    override val nodes: List<ReadAloudNode>,
    override val emptyNodes: Set<ReadAloudNode>,
) : ReadAloudSegment

internal data class TtsSegment(
    override val engine: TtsEngine,
    val items: List<GuidedNavigationText>,
    override val nodes: List<ReadAloudNode>,
    override val emptyNodes: Set<ReadAloudNode>,
) : ReadAloudSegment

internal class ReadAloudSegmentFactory(
    private val audioEngineFactory: () -> AudioEngine,
    private val ttsEngineFactory: () -> TtsEngine,
) {

    fun createSegmentFromNode(node: ReadAloudNode): ReadAloudSegment? =
        createAudioSegmentFromNode(node)
            .takeUnless { it.items.isEmpty() }
            ?: createTtsSegmentFromNode(node)
                .takeUnless { it.items.isEmpty() }

    private fun createAudioSegmentFromNode(
        firstNode: ReadAloudNode,
    ): AudioSegment {
        var nextNode: ReadAloudNode? = firstNode
        val audioItems = mutableListOf<AudioEngine.Item>()
        val nodes = mutableListOf<ReadAloudNode>()
        val emptyNodes = mutableSetOf<ReadAloudNode>()

        while (nextNode != null && nextNode.content !is TextContent) {
            val audioItem = (nextNode.content as? AudioContent)?.toAudioItem()

            if (audioItem != null) {
                audioItems.add(audioItem)
                nodes.add(nextNode)
            } else {
                emptyNodes.add(nextNode)
            }

            nextNode = nextNode.next()
        }

        val audioEngine = audioEngineFactory()
        audioEngine.playWhenReady = false
        audioEngine.setPlaylist(audioItems)

        return AudioSegment(
            engine = audioEngine,
            items = audioItems,
            nodes = nodes,
            emptyNodes = emptyNodes
        )
    }

    private fun createTtsSegmentFromNode(
        firstNode: ReadAloudNode,
    ): TtsSegment {
        var nextNode: ReadAloudNode? = firstNode
        val textItems = mutableListOf<GuidedNavigationText>()
        val nodes = mutableListOf<ReadAloudNode>()
        val emptyNodes = mutableSetOf<ReadAloudNode>()

        while (nextNode != null && nextNode.content !is AudioContent) {
            val textContent = (nextNode.content as? TextContent)

            if (textContent != null) {
                textItems.add(textContent.text)
                nodes.add(nextNode)
            } else {
                emptyNodes.add(nextNode)
            }

            nextNode = nextNode.next()
        }

        return TtsSegment(
            engine = ttsEngineFactory(),
            items = textItems,
            nodes = nodes,
            emptyNodes = emptyNodes
        )
    }

    private val ReadAloudNode.content: NodeContent? get() {
        refs
            .firstNotNullOfOrNull { it as? GuidedNavigationAudioRef }
            ?.let {
                return AudioContent(
                    href = it.url.removeFragment(),
                    interval = it.url.timeInterval
                )
            }

        text
            ?.let {
                return TextContent(it)
            }

        return null
    }
}

private sealed interface NodeContent

private data class AudioContent(
    val href: Url,
    val interval: TimeInterval?,
) : NodeContent

private data class TextContent(
    val text: GuidedNavigationText,
) : NodeContent

private fun AudioContent.toAudioItem(): AudioEngine.Item {
    return AudioEngine.Item(
        href = href,
        interval = interval
    )
}

private val Url.timeInterval get() = fragment
    ?.let { TemporalFragmentParser.parse(it) }
