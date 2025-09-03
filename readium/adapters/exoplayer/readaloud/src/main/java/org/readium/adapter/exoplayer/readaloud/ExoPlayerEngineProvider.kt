/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.adapter.exoplayer.readaloud

import android.app.Application
import org.readium.adapter.exoplayer.audio.ExoPlayerDataSource
import org.readium.navigator.media.readaloud.AudioEngine
import org.readium.navigator.media.readaloud.AudioEngineProvider
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.publication.Publication

@OptIn(InternalReadiumApi::class)
@ExperimentalReadiumApi
public class ExoPlayerEngineProvider(
    private val application: Application,
) : AudioEngineProvider {

    override fun createEngine(
        publication: Publication,
        playlist: List<AudioEngine.Item>,
        listener: AudioEngine.Listener,
    ): ExoPlayerEngine {
        val dataSourceFactory = ExoPlayerDataSource.Factory(publication)
        return ExoPlayerEngine(application, dataSourceFactory, playlist, listener)
    }
}
