/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.navigator.media.readaloud

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Language

@ExperimentalReadiumApi
public interface PlaybackEngine {

    public interface Listener {

        public fun onUtterancesReady()

        public fun onPlaybackCompleted()
    }

    public fun feed(utterances: List<String>)

    public fun speak(utteranceIndex: Int)
}

@ExperimentalReadiumApi
public interface PausablePlaybackEngine : PlaybackEngine {

    public fun pause()

    public fun resume()
}

@ExperimentalReadiumApi
public interface PlaybackEngineProvider<V : PlaybackEngineProvider.Voice> {

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

    public fun createEngine(voice: V): PlaybackEngine
}
