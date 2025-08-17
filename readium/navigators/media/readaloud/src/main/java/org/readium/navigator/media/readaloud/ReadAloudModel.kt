/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.navigator.media.readaloud

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.guided.GuidedNavigationRef
import org.readium.r2.shared.guided.GuidedNavigationRole
import org.readium.r2.shared.guided.GuidedNavigationText

//  Modèle différent pour pouvoir garder une référence sur le noeud parent.
@ExperimentalReadiumApi
public sealed interface ReadAloudNode {

    public val roles: Set<GuidedNavigationRole>

    public val parent: ReadAloudNode?

    public val children: List<ReadAloudNode>
}

@ExperimentalReadiumApi
public class ReadAloudInnerNode(
    override val children: List<ReadAloudNode>,
    override val roles: Set<GuidedNavigationRole>,
) : ReadAloudNode {

    override var parent: ReadAloudNode? = null
        internal set
}

@ExperimentalReadiumApi
public data class ReadAloudLeafNode(
    val text: GuidedNavigationText?,
    val refs: Set<GuidedNavigationRef>,
    override val roles: Set<GuidedNavigationRole>,
) : ReadAloudNode {

    override val children: List<ReadAloudNode> =
        emptyList()

    override lateinit var parent: ReadAloudInnerNode
        internal set
}

@ExperimentalReadiumApi
internal fun ReadAloudNode.isSkippable(): Boolean =
    nearestSkippable() != null

@ExperimentalReadiumApi
internal fun ReadAloudNode.isEscapable(): Boolean =
    nearestEscapable() != null

@ExperimentalReadiumApi
internal fun ReadAloudNode.firstLeaf(): ReadAloudLeafNode? =
    when (this) {
        is ReadAloudLeafNode -> this
        is ReadAloudInnerNode -> children[0].firstLeaf()
    }

@ExperimentalReadiumApi
internal fun ReadAloudNode.nextLeaf(): ReadAloudLeafNode? {
    if (children.isNotEmpty()) {
        return children[0].nextLeaf()
    }

    val siblings = parent?.children ?: return null
    val currentIndex = siblings.indexOf(this)
    check(currentIndex != -1)

    return currentIndex
        .takeIf { it < siblings.size - 1 }
        ?.let { siblings[currentIndex + 1].nextLeaf() }
        ?: parent!!.skipToNext()?.nextLeaf()
}

@ExperimentalReadiumApi
internal fun ReadAloudNode.skipToNext(): ReadAloudNode? {
    val siblings = parent?.children ?: return null
    val currentIndex = siblings.indexOf(this)
    check(currentIndex != -1)
    return currentIndex
        .takeIf { it < siblings.size - 1 }
        ?.let { siblings[currentIndex + 1] }
        ?: parent!!.skipToNext()
}

@ExperimentalReadiumApi
internal fun ReadAloudNode.skipToPrevious(): ReadAloudNode? {
    val siblings = parent?.children ?: return null
    val currentIndex = siblings.indexOf(this)
    check(currentIndex != -1)
    return currentIndex
        .takeIf { it > 0 }
        ?.let { siblings[currentIndex - 1] }
        ?: parent!!.skipToPrevious()
}

@ExperimentalReadiumApi
internal fun ReadAloudNode.escape(force: Boolean): ReadAloudNode? =
    (nearestEscapable() ?: this.takeIf { force })?.skipToNext()

@ExperimentalReadiumApi
internal fun ReadAloudNode.skip(force: Boolean): ReadAloudNode? =
    (nearestSkippable() ?: this.takeIf { force })?.skipToNext()

@ExperimentalReadiumApi
private fun ReadAloudNode.nearestEscapable(): ReadAloudNode? =
    nearestOrNull { roles.any { it in GuidedNavigationRole.ESCAPABLE_ROLES } }

@ExperimentalReadiumApi
private fun ReadAloudNode.nearestSkippable(): ReadAloudNode? =
    nearestOrNull { roles.any { it in GuidedNavigationRole.SKIPPABLE_ROLES } }

@ExperimentalReadiumApi
private fun ReadAloudNode.nearestOrNull(
    predicate: (ReadAloudNode) -> Boolean,
): ReadAloudNode? =
    when {
        predicate(this) -> this
        parent == null -> null
        else -> parent!!.nearestOrNull(predicate)
    }
