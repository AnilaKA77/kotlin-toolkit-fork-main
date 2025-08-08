/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.shared.publication.services

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.guided.GuidedNavigationDocument
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.PublicationServicesHolder
import org.readium.r2.shared.util.Closeable
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.data.ReadError

@ExperimentalReadiumApi
public interface GuidedNavigationService : Publication.Service {

    public fun iterator(): GuidedNavigationIterator
}

@ExperimentalReadiumApi
public interface GuidedNavigationIterator : Closeable {

    /**
     * Prepares an element for retrieval by the invocation of next.
     *
     * Does nothing if the the end has been reached.
     */
    public suspend operator fun hasNext(): Boolean

    /**
     * Retrieves the next guided navigation document, prepared by the preceding call to [hasNext],
     * or throws an IllegalStateException if hasNext was not invoked.
     */
    public suspend operator fun next(): Try<GuidedNavigationDocument, ReadError>

    /**
     * Closes any resources allocated by the iterator.
     */
    override fun close() {}
}

@ExperimentalReadiumApi
public val PublicationServicesHolder.guidedNavigationService: GuidedNavigationService?
    get() {
        findService(GuidedNavigationService::class)?.let { return it }
        return null
    }
