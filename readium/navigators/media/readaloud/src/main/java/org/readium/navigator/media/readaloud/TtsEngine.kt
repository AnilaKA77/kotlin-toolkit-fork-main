/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.navigator.media.readaloud

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.util.Language

@ExperimentalReadiumApi
public interface TtsEngine {

    public interface Listener<E : Error> {

        public fun onReady()

        public fun onDone()

        public fun onInterrupted()

        /**
         * Called when the [TtsEngine] is about to speak the specified [range] of the utterance with
         * the given id.
         *
         * This callback may not be called if the [TtsEngine] does not provide range information.
         */
        public fun onRange(range: IntRange)

        /**
         * Called when an error has occurred during processing of the utterance with the given id.
         */
        public fun onError(error: E)
    }

    public val utterances: List<String>

    public fun prepare()

    public fun speak(utteranceIndex: Int)

    public fun stop()

    public fun release()
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
public interface TtsEngineProvider<V : TtsVoice, E : Error> {

    /**
     * Sets of voices available with this [TtsEngineProvider].
     */
    public val voices: Set<V>

    public fun createEngine(
        voice: V,
        utterances: List<String>,
        listener: TtsEngine.Listener<E>,
    ): TtsEngine
}

@ExperimentalReadiumApi
public interface PausableTtsEngine : TtsEngine {

    public fun pause()

    public fun resume()
}

internal class PauseDecorator(
    private val engine: TtsEngine,
) : PausableTtsEngine, TtsEngine by engine {

    private var currentIndex: Int = 0

    override fun pause() {
        engine.stop()
    }

    override fun resume() {
        engine.speak(currentIndex)
    }

    override fun speak(utteranceIndex: Int) {
        currentIndex = utteranceIndex
        engine.speak(utteranceIndex)
    }
}
