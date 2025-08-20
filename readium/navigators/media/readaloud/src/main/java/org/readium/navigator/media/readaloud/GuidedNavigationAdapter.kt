/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.navigator.media.readaloud

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.guided.GuidedNavigationObject

@OptIn(ExperimentalReadiumApi::class)
internal class GuidedNavigationAdapter {

    fun adapt(guidedNavTree: GuidedNavigationObject): ReadAloudInnerNode {
        val children = guidedNavTree.children.mapNotNull { adaptNode(it) }
        val node = ReadAloudInnerNode(
            children = children,
            roles = guidedNavTree.roles,
            refs = guidedNavTree.refs
        )
        setParentInChildren(node)
        return node
    }

    private fun adaptNode(guidedNavigationObject: GuidedNavigationObject): ReadAloudNode? {
        return when (guidedNavigationObject.children.size) {
            0 ->
                adaptLeat(guidedNavigationObject)
            else ->
                guidedNavigationObject.children
                    .mapNotNull { adaptNode(it) }
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        ReadAloudInnerNode(
                            children = it,
                            roles = guidedNavigationObject.roles,
                            refs = guidedNavigationObject.refs
                        )
                    }
                    ?.also { setParentInChildren(it) }
        }
    }

    private fun adaptLeat(guidedNavigationLeaf: GuidedNavigationObject): ReadAloudLeafNode? {
        return ReadAloudLeafNode(
            text = guidedNavigationLeaf.text,
            refs = guidedNavigationLeaf.refs,
            roles = guidedNavigationLeaf.roles
        )
    }

    private fun setParentInChildren(parent: ReadAloudInnerNode) {
        parent.children.forEach {
            when (it) {
                is ReadAloudInnerNode -> it.parent = parent
                is ReadAloudLeafNode -> it.parent = parent
            }
        }
    }
}
