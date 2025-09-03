/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.navigator.media.readaloud

import org.readium.navigator.common.CssSelector
import org.readium.navigator.common.CssSelectorLocation
import org.readium.navigator.common.ExportableLocation
import org.readium.navigator.common.GoLocation
import org.readium.navigator.common.Location
import org.readium.navigator.common.TextAnchor
import org.readium.navigator.common.TextAnchorLocation
import org.readium.navigator.common.TextQuote
import org.readium.navigator.common.TextQuoteLocation
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Locator.Locations
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@ExperimentalReadiumApi
public data class ReadAloudGoLocation(
    override val href: Url,
    val cssSelector: CssSelector?,
    val textAnchor: TextAnchor?,
) : GoLocation {

    public constructor(location: Location) : this(
        href = location.href,
        cssSelector = (location as? CssSelectorLocation)?.cssSelector,
        textAnchor = (location as? TextAnchorLocation)?.textAnchor
    )
}

@ExperimentalReadiumApi
public sealed interface ReadAloudLocation : ExportableLocation {
    override val href: Url
    public val textAnchor: TextAnchor?
    public val cssSelector: CssSelector?
}

@ExperimentalReadiumApi
internal data class MediaOverlaysLocation(
    override val href: Url,
    private val mediaType: MediaType?,
    override val cssSelector: CssSelector?,
) : ReadAloudLocation, CssSelectorLocation {

    override fun toLocator(): Locator =
        Locator(
            href = href,
            mediaType = mediaType ?: MediaType.XHTML,
            locations = Locations() // TODO
        )

    override val textAnchor: TextAnchor? = null
}

@ExperimentalReadiumApi
internal data class TtsLocation(
    override val href: Url,
    private val mediaType: MediaType?,
    override val cssSelector: CssSelector?,
    override val textAnchor: TextAnchor,
) : ReadAloudLocation, CssSelectorLocation, TextAnchorLocation {

    override fun toLocator(): Locator =
        Locator(
            href = href,
            mediaType = mediaType ?: MediaType.XHTML,
            locations = Locations() // TODO
        )
}

@ExperimentalReadiumApi
public sealed interface UtteranceLocation : ExportableLocation {
    override val href: Url

    public val textQuote: TextQuote?
    public val cssSelector: CssSelector?
}

internal data class MediaOverlaysUtteranceLocation(
    override val href: Url,
    private val mediaType: MediaType?,
    override val cssSelector: CssSelector?,
) : UtteranceLocation, CssSelectorLocation {

    override val textQuote: TextQuote? = null

    override fun toLocator(): Locator {
        TODO("Not yet implemented")
    }
}

internal data class TtsUtteranceLocation(
    override val href: Url,
    private val mediaType: MediaType?,
    override val cssSelector: CssSelector?,
    override val textQuote: TextQuote,
) : UtteranceLocation, CssSelectorLocation, TextQuoteLocation {

    override fun toLocator(): Locator {
        TODO("Not yet implemented")
    }
}
