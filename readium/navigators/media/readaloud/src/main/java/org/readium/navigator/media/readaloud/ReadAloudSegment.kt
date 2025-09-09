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
import org.readium.r2.shared.guided.GuidedNavigationTextRef
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.TemporalFragmentParser
import org.readium.r2.shared.util.TimeInterval
import org.readium.r2.shared.util.Url

internal sealed interface ReadAloudSegment {

    val nodes: List<ReadAloudNode>

    val player: SegmentPlayer

    val emptyNodes: Set<ReadAloudNode>
}

internal data class AudioSegment(
    override val player: AudioSegmentPlayer,
    val items: List<AudioEngine.Item>,
    val textRefs: List<Url>,
    override val nodes: List<ReadAloudNode>,
    override val emptyNodes: Set<ReadAloudNode>,
) : ReadAloudSegment

internal data class TtsSegment<E : Error>(
    override val player: TtsSegmentPlayer<E>,
    val items: List<GuidedNavigationText>,
    override val nodes: List<ReadAloudNode>,
    override val emptyNodes: Set<ReadAloudNode>,
) : ReadAloudSegment

internal class ReadAloudSegmentFactory<E : Error>(
    private val audioEngineFactory: (List<AudioEngine.Item>) -> AudioEngine?,
    private val ttsPlayerFactory: (Language?, List<String>) -> TtsPlayer<E>?,
) {

    fun createSegmentFromNode(node: ReadAloudNode): ReadAloudSegment? =
        createAudioSegmentFromNode(node)
            ?: createTtsSegmentFromNode(node)

    private fun createAudioSegmentFromNode(
        firstNode: ReadAloudNode,
    ): AudioSegment? {
        var nextNode: ReadAloudNode? = firstNode
        val audioItems = mutableListOf<AudioEngine.Item>()
        val textRefs = mutableListOf<Url>()
        val nodes = mutableListOf<ReadAloudNode>()
        val emptyNodes = mutableSetOf<ReadAloudNode>()

        while (nextNode != null && nextNode.content !is TextContent) {
            val audioContent = (nextNode.content as? AudioContent)

            if (audioContent != null) {
                audioItems.add(audioContent.toAudioItem())
                nodes.add(nextNode)
                textRefs.add(audioContent.textRef)
            } else {
                emptyNodes.add(nextNode)
            }

            nextNode = nextNode.next()
        }

        if (audioItems.isEmpty()) {
            return null
        }

        val audioEngine = audioEngineFactory(audioItems)
            ?: return null

        audioEngine.playWhenReady = false

        return AudioSegment(
            player = AudioSegmentPlayer(audioEngine),
            items = audioItems,
            textRefs = textRefs,
            nodes = nodes,
            emptyNodes = emptyNodes
        )
    }

    private fun createTtsSegmentFromNode(
        firstNode: ReadAloudNode,
    ): TtsSegment<E>? {
        var nextNode: ReadAloudNode? = firstNode
        val segmentLanguage = firstNode.text?.language
        val textItems = mutableListOf<GuidedNavigationText>()
        val nodes = mutableListOf<ReadAloudNode>()
        val emptyNodes = mutableSetOf<ReadAloudNode>()

        while (
            nextNode != null &&
            nextNode.content !is AudioContent &&
            nextNode.language == segmentLanguage
        ) {
            val textContent = (nextNode.content as? TextContent)

            if (textContent != null) {
                textItems.add(textContent.text)
                nodes.add(nextNode)
            } else {
                emptyNodes.add(nextNode)
            }

            nextNode = nextNode.next()
        }

        if (textItems.isEmpty()) {
            return null
        }

        val utterances = textItems.map { it.plain!! }

        val ttsPlayer = ttsPlayerFactory(segmentLanguage, utterances)
            ?: return null

        return TtsSegment(
            player = TtsSegmentPlayer(ttsPlayer),
            items = textItems,
            nodes = nodes,
            emptyNodes = emptyNodes
        )
    }

    private val ReadAloudNode.language: Language? get() = when (content) {
        is TextContent -> text?.language
        else -> null
    }

    private val ReadAloudNode.content: NodeContent? get() {
        refs
            .firstNotNullOfOrNull { it as? GuidedNavigationAudioRef }
            ?.let { audioRef ->

                val textRef = refs.firstNotNullOfOrNull { it as? GuidedNavigationTextRef }

                // Ignore audio nodes without textref. Might be changed later.
                textRef?.let {
                    return AudioContent(
                        href = audioRef.url.removeFragment(),
                        interval = audioRef.url.timeInterval,
                        textRef = it.url
                    )
                }
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
    val textRef: Url,
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
