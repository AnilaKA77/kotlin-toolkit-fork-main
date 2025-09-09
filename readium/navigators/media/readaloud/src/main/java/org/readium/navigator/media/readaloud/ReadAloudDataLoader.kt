/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.navigator.media.readaloud

import kotlin.properties.Delegates
import org.readium.navigator.media.readaloud.preferences.ReadAloudSettings
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Error

internal class ReadAloudDataLoader<E : Error>(
    private val segmentFactory: ReadAloudSegmentFactory<E>,
    initialSettings: ReadAloudSettings,
) {
    data class ItemRef(
        val segment: ReadAloudSegment,
        val nodeIndex: Int?,
    )

    var settings by Delegates.observable(initialSettings) { property, oldValue, newValue ->
        preloadedRefs.clear()
    }

    private val preloadedRefs: MutableMap<ReadAloudNode, ItemRef> = mutableMapOf()

    fun onPlaybackProgressed(node: ReadAloudNode) {
        val nextNode = node.next() ?: return

        if (nextNode !in preloadedRefs) {
            // loadSegmentForNode(nextNode, true)
        }
    }

    fun getItemRef(node: ReadAloudNode, prepare: Boolean = true): ItemRef? {
        loadSegmentForNode(node, prepare)
        return preloadedRefs[node]
    }

    private fun loadSegmentForNode(node: ReadAloudNode, prepare: Boolean): ReadAloudSegment? {
        if (node in preloadedRefs) {
            return null
        }

        val segment = segmentFactory.createSegmentFromNode(node)
            ?: return null // Ended

        if (prepare) {
            segment.player.prepare()
        }

        val refs = computeRefsForSegment(segment)
        preloadedRefs.putAll(refs)

        return segment
    }

    private fun computeRefsForSegment(segment: ReadAloudSegment): Map<ReadAloudNode, ItemRef> {
        val plainRefs = segment.nodes
            .withIndex()
            .associate { (index, node) -> node to ItemRef(segment, index) }

        val emptyRefs = segment.emptyNodes.associateWith { ItemRef(segment, null) }

        return plainRefs + emptyRefs
    }
}
