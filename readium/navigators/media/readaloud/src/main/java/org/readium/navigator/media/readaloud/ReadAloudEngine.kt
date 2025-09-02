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

@ExperimentalReadiumApi
public sealed interface ReadAloudEngine {

    public var playWhenReady: Boolean
}

@ExperimentalReadiumApi
public interface TtsEngine : ReadAloudEngine {

    public interface Listener {

        public fun onUtterancesReady()

        public fun onPlaybackCompleted()
    }

    public fun feed(utterances: List<String>)

    public fun speak(utteranceIndex: Int)
}

@ExperimentalReadiumApi
public interface PausableTtsEngine : TtsEngine {

    public fun pause()

    public fun resume()
}

@ExperimentalReadiumApi
public interface TtsVoice {

    @JvmInline
    public value class Id(public val value: String)

    public val id: Id

    /**
     * The languages supported by the voice.
     */
    public val languages: Set<Language>
}

@ExperimentalReadiumApi
public interface TtsEngineProvider {

    /**
     * Sets of voices available with this [TtsEngineProvider].
     */
    public val voices: Set<TtsVoice>

    public fun createEngine(voice: TtsVoice): TtsEngine
}

@ExperimentalReadiumApi
public object NullTtsEngineProvider : TtsEngineProvider {

    override val voices: Set<TtsVoice> = emptySet()

    override fun createEngine(voice: TtsVoice): TtsEngine {
        throw IllegalStateException()
    }
}

@ExperimentalReadiumApi
public interface AudioEngine : ReadAloudEngine {

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

    public fun setPlaylist(items: List<Item>)

    public fun seekTo(index: Int)
}

@ExperimentalReadiumApi
public interface AudioEngineProvider {

    public fun createEngine(
        publication: Publication,
        listener: AudioEngine.Listener,
    ): AudioEngine
}
