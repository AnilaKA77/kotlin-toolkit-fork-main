/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.navigator.media.readaloud

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.services.GuidedNavigationIterator
import org.readium.r2.shared.publication.services.GuidedNavigationService

internal class ContentIteratorGuidedNavigationService : GuidedNavigationService {

    override fun iterator(): GuidedNavigationIterator {
        TODO("Not yet implemented")
    }
}
