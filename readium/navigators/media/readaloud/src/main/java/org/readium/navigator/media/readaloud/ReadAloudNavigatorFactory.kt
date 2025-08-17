/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.navigator.media.readaloud

import android.app.Application
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.guided.GuidedNavigationContainer
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.GuidedNavigationService
import org.readium.r2.shared.publication.services.guidedNavigationService
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.getOrElse

@ExperimentalReadiumApi
public class ReadAloudNavigatorFactory private constructor(
    private val guidedNavigationService: GuidedNavigationService,
    private val audioEngineFactory: (AudioEngine.Listener) -> AudioEngine,
) {

    public companion object {

        public operator fun invoke(
            application: Application,
            publication: Publication,
            audioEngineProvider: AudioEngineProvider,
        ): ReadAloudNavigatorFactory? {
            val guidedNavService = publication.guidedNavigationService
                ?: return null

            val audioEngineFactory = { listener: AudioEngine.Listener ->
                audioEngineProvider.createEngine(publication, listener)
            }

            return ReadAloudNavigatorFactory(
                guidedNavigationService = guidedNavService,
                audioEngineFactory = audioEngineFactory
            )
        }
    }

    public sealed class Error(
        override val message: String,
        override val cause: org.readium.r2.shared.util.Error?,
    ) : org.readium.r2.shared.util.Error {

        public class UnsupportedPublication(
            cause: org.readium.r2.shared.util.Error? = null,
        ) : Error("Publication is not supported.", cause)

        public class GuidedNavigationService(
            override val cause: ReadError,
        ) : Error("Failed to acquire guided navigation documents.", cause)
    }

    public suspend fun createNavigator(): Try<ReadAloudNavigator, Error> {
        val guidedDocs = buildList {
            val iterator = guidedNavigationService.iterator()
            while (iterator.hasNext()) {
                val doc = iterator.next().getOrElse {
                    return Try.failure(Error.GuidedNavigationService(it))
                }
                add(doc)
            }
        }

        val guidedTree = GuidedNavigationContainer(
            children = guidedDocs.map {
                GuidedNavigationContainer(it.guided, emptySet())
            },
            roles = emptySet()
        )

        val navigator = ReadAloudNavigator(
            guidedNavigationTree = guidedTree,
            audioEngineFactory = audioEngineFactory
        )

        return Try.success(navigator)
    }
}
