/*
 * Copyright 2023 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.shared.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Temporal dimension parser for
 * [Media Fragment specification](https://www.w3.org/TR/media-frags/#naming-time).
 *
 * Supports only Normal Play Time specified as seconds without fractional part at the moment.
 */
public object TemporalFragmentParser {

    public fun parse(value: String): TimeInterval? {
        if (!value.startsWith("t=")) {
            return null
        }

        val nptValue = value.removePrefix("t=").removePrefix("npt:")
        return parseNormalPlayTime(nptValue)
    }

    private fun parseNormalPlayTime(value: String): TimeInterval? {
        val regex = """(\d+)?(,\d+)?""".toRegex()
        val result = regex.matchEntire(value)
            ?: return null

        val startOffset = result.groupValues[1]
            .takeIf { it.isNotEmpty() }
            ?.toIntOrNull()
            ?.seconds

        val endOffset = result.groupValues[2]
            .removePrefix(",")
            .takeIf { it.isNotEmpty() }
            ?.toIntOrNull()
            ?.seconds

        return TimeInterval(startOffset, endOffset)
    }
}

public data class TimeInterval(
    val start: Duration?,
    val end: Duration?,
)
