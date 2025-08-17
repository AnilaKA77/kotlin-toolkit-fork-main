/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.shared.guided

@JvmInline
public value class GuidedNavigationRole(public val value: String) {

    public companion object {

        /**
         * Inherited from HTML and/or ARIA
         */
        public val ASIDE: GuidedNavigationRole = GuidedNavigationRole("aside")
        public val CELL: GuidedNavigationRole = GuidedNavigationRole("cell")
        public val DEFINITION: GuidedNavigationRole = GuidedNavigationRole("definition")
        public val FIGURE: GuidedNavigationRole = GuidedNavigationRole("figure")
        public val LIST: GuidedNavigationRole = GuidedNavigationRole("list")
        public val LIST_ITEM: GuidedNavigationRole = GuidedNavigationRole("listItem")
        public val ROW: GuidedNavigationRole = GuidedNavigationRole("row")
        public val TABLE: GuidedNavigationRole = GuidedNavigationRole("table")
        public val TERM: GuidedNavigationRole = GuidedNavigationRole("term")

        /**
         * Inherited from EPUB 3 Structural Semantics Vocabulary 1.1
         */
        public val LANDMARKS: GuidedNavigationRole = GuidedNavigationRole("landmarks")
        public val LOA: GuidedNavigationRole = GuidedNavigationRole("loa")
        public val LOI: GuidedNavigationRole = GuidedNavigationRole("loi")
        public val LOT: GuidedNavigationRole = GuidedNavigationRole("lot")
        public val LOV: GuidedNavigationRole = GuidedNavigationRole("lov")

        public val SKIPPABLE_ROLES: List<GuidedNavigationRole> =
            listOf()

        public val ESCAPABLE_ROLES: List<GuidedNavigationRole> =
            listOf()
    }
}
