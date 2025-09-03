/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.navigator.media.readaloud

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Error

internal sealed interface SegmentPlayer {

    val player: Any

    var playWhenReady: Boolean

    fun prepare()

    fun seekTo(index: Int)

    fun release()
}

internal class TtsSegmentPlayer<E : Error>(
    override val player: TtsPlayer<E>,
) : SegmentPlayer {

    override var playWhenReady: Boolean
        get() = player.playWhenReady
        set(value) {
            player.playWhenReady = value
        }

    override fun prepare() {
        player.prepare()
    }

    override fun seekTo(index: Int) {
        player.seekTo(index)
    }

    override fun release() {
        player.release()
    }
}

internal class AudioSegmentPlayer(
    override val player: AudioEngine,
) : SegmentPlayer {

    override var playWhenReady: Boolean
        get() = player.playWhenReady
        set(value) {
            player.playWhenReady = value
        }

    override fun prepare() {
    }

    override fun seekTo(index: Int) {
        player.seekTo(index)
    }

    override fun release() {
        player.release()
    }
}
