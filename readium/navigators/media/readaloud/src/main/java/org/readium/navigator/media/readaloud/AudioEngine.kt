/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.navigator.media.readaloud

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.TimeInterval
import org.readium.r2.shared.util.Url

@ExperimentalReadiumApi
public interface AudioEngine {

    public interface Listener {

        public fun onItemChanged(engine: AudioEngine, index: Int)

        public fun onStateChanged(engine: AudioEngine, state: State)
    }

    public enum class State {
        Ready,
        Starved,
        Ended,
    }

    public data class Item(
        val href: Url,
        val interval: TimeInterval?,
    )

    public var playWhenReady: Boolean

    public val playlist: List<Item>

    public fun seekTo(index: Int)

    public fun release()
}

@ExperimentalReadiumApi
public interface AudioEngineProvider {

    public fun createEngine(
        publication: Publication,
        playlist: List<AudioEngine.Item>,
        listener: AudioEngine.Listener,
    ): AudioEngine
}
