/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.navigator.media.readaloud

import android.app.Application
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.guided.GuidedNavigationObject
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.epub.MediaOverlaysService
import org.readium.r2.shared.publication.services.GuidedNavigationService
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.getOrElse

@ExperimentalReadiumApi
public class ReadAloudNavigatorFactory private constructor(
    private val guidedNavigationService: GuidedNavigationService,
    private val resources: List<ReadAloudPublication.Item>,
    private val audioEngineFactory: (AudioEngine.Listener) -> AudioEngine,
    private val ttsEngineFactory: () -> TtsEngine,
) {

    public companion object {

        public operator fun invoke(
            application: Application,
            publication: Publication,
            audioEngineProvider: AudioEngineProvider,
            ttsEngineProvider: TtsEngineProvider,
            usePrerecordedVoicesWhenAvailable: Boolean = true,
        ): ReadAloudNavigatorFactory? {
            var guidedNavService: GuidedNavigationService? = null

            if (usePrerecordedVoicesWhenAvailable) {
                guidedNavService = publication.findService(MediaOverlaysService::class)
            }
            if (guidedNavService == null) {
                guidedNavService = publication.findService(GuidedNavigationService::class)
            }

            if (guidedNavService == null) {
                return null
            }

            val audioEngineFactory = { listener: AudioEngine.Listener ->
                audioEngineProvider.createEngine(publication, listener)
            }

            val ttsEngineFactory = {
                val voice = ttsEngineProvider.voices.first()
                ttsEngineProvider.createEngine(voice)
            }

            val resources = (publication.readingOrder + publication.resources).map {
                ReadAloudPublication.Item(
                    href = it.url(),
                    mediaType = it.mediaType
                )
            }

            return ReadAloudNavigatorFactory(
                guidedNavigationService = guidedNavService,
                resources = resources,
                audioEngineFactory = audioEngineFactory,
                ttsEngineFactory = ttsEngineFactory
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

    public suspend fun createNavigator(
        initialSettings: ReadAloudSettings,
        initialLocation: ReadAloudGoLocation? = null,
    ): Try<ReadAloudNavigator, Error> {
        val guidedDocs = buildList {
            val iterator = guidedNavigationService.iterator()
            while (iterator.hasNext()) {
                val doc = iterator.next().getOrElse {
                    return Try.failure(Error.GuidedNavigationService(it))
                }
                add(doc)
            }
        }

        val guidedTree = GuidedNavigationObject(
            children = guidedDocs.map {
                GuidedNavigationObject(it.guided, roles = emptySet(), refs = emptySet(), text = null)
            },
            roles = emptySet(),
            refs = emptySet(),
            text = null
        )

        val navigatorPublication = ReadAloudPublication(
            guidedNavigationTree = guidedTree,
            resources = resources,
        )

        val navigator = ReadAloudNavigator(
            initialSettings = initialSettings,
            initialLocation = initialLocation,
            publication = navigatorPublication,
            audioEngineFactory = audioEngineFactory,
            ttsEngineFactory = ttsEngineFactory
        )

        return Try.success(navigator)
    }
}
