/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.navigator.media.readaloud

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.TimeInterval
import org.readium.r2.shared.util.Url

public sealed interface ReadAloudEngine

public interface AudioEngine : ReadAloudEngine {

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

public interface AudioEngineProvider {

    public fun createEngine(
        publication: Publication,
        listener: AudioEngine.Listener,
    ): AudioEngine
}

@ExperimentalReadiumApi
public interface PlaybackEngine : ReadAloudEngine {

    public fun play(node: ReadAloudLeafNode)
}

@ExperimentalReadiumApi
public interface TtsEngine<V : TtsEngine.Voice> : PlaybackEngine {

    public interface Voice {

        /**
         * The languages supported by the voice.
         */
        public val languages: Set<Language>
    }

    /**
     * Sets of voices available with this [PlaybackEngine].
     */
    public val voices: Set<V>
}
