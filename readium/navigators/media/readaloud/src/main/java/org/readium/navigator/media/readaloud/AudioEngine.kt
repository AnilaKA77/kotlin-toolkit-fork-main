/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.navigator.media.readaloud

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.TimeInterval
import org.readium.r2.shared.util.Url

@ExperimentalReadiumApi
public interface AudioEngine {

    public interface Listener {

        public fun onItemChanged(index: Int)

        public fun onPlaybackEnded()
    }

    public data class Item(
        val href: Url,
        val interval: TimeInterval?,
    )

    public fun setPlaylist(items: List<Item>)

    public fun pause()

    public fun resume()
}

@ExperimentalReadiumApi
public interface AudioEngineProvider {

    public fun createEngine(
        publication: Publication,
        listener: AudioEngine.Listener,
    ): AudioEngine
}
