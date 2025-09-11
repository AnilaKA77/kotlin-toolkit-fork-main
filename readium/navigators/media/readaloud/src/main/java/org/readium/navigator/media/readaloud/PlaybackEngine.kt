/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.navigator.media.readaloud

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.TimeInterval
import org.readium.r2.shared.util.Url

@ExperimentalReadiumApi
public interface AudioEngineProvider {

    public fun createEngineFactory(
        publication: Publication,
    ): AudioEngineFactory
}

@ExperimentalReadiumApi
public interface AudioEngineFactory {

    public fun createPlaybackEngine(
        chunks: List<AudioChunk>,
        listener: PlaybackEngine.Listener,
    ): PlaybackEngine
}

@ExperimentalReadiumApi
public data class AudioChunk(
    val href: Url,
    val interval: TimeInterval?,
)

@ExperimentalReadiumApi
public interface TtsEngineProvider<V : TtsVoice, E : Error> {

    public suspend fun createEngineFactory(): TtsEngineFactory<V, E>
}

@ExperimentalReadiumApi
public interface TtsEngineFactory<V : TtsVoice, E : Error> {

    /**
     * Sets of voices available with this [TtsEngineFactory].
     */
    public val voices: Set<V>

    public fun createPlaybackEngine(
        voice: V,
        utterances: List<String>,
        listener: PlaybackEngine.Listener,
    ): PlaybackEngine
}

internal class NullPlaybackEngineFactory<V : TtsVoice, E : Error>() : TtsEngineFactory<V, E> {

    override val voices: Set<V> = emptySet()

    override fun createPlaybackEngine(
        voice: V,
        utterances: List<String>,
        listener: PlaybackEngine.Listener,
    ): PlaybackEngine {
        throw IllegalArgumentException("Unknown voice.")
    }
}

@ExperimentalReadiumApi
public interface TtsVoice {

    @kotlinx.serialization.Serializable
    @JvmInline
    public value class Id(public val value: String)

    public val id: Id

    /**
     * The languages supported by the voice.
     */
    public val languages: Set<Language>
}

@ExperimentalReadiumApi
public interface PlaybackEngine {

    public var pitch: Double

    public var speed: Double

    /**
     * Sets the index of the item to play on the next call to [start].
     */
    public var itemToPlay: Int

    public enum class PlaybackState {
        Playing,
        Starved,
    }

    public interface Listener {

        public fun onStartRequested(initialState: PlaybackState)

        public fun onStopRequested()

        public fun onPlaybackCompleted()

        public fun onPlaybackStateChanged(state: PlaybackState)

        public fun onRangeStarted(range: IntRange)
    }

    /**
     * Starts playing the [itemToPlay]-th item.
     *
     * The state will become either [PlaybackState.Playing] or [PlaybackState.Starved].
     */
    public fun start()

    /**
     * Stops ongoing playback.
     *
     * Makes the state become [PlaybackState.Idle]
     */
    public fun stop()

    public fun pause()

    public fun resume()

    /**
     * Free all used resources.
     */
    public fun release()
}
