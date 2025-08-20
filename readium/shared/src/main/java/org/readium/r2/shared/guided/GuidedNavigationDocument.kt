/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.shared.guided

import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.Url

public data class GuidedNavigationDocument(
    val links: List<Link>,
    val guided: List<GuidedNavigationObject>,
)
public data class GuidedNavigationObject(
    val children: List<GuidedNavigationObject>,
    val roles: Set<GuidedNavigationRole>,
    val refs: Set<GuidedNavigationRef>,
    val text: GuidedNavigationText?,
)

@JvmInline
public value class SsmlString(public val value: String)

@ConsistentCopyVisibility
public data class GuidedNavigationText private constructor(
    val plain: String?,
    val ssml: SsmlString?,
    val language: Language?,
) {
    init {
        require(plain != null || ssml != null)
        require(plain == null || plain.isNotEmpty())
        require(ssml == null || ssml.value.isNotEmpty())
    }
}

public sealed interface GuidedNavigationRef {
    public val url: Url
}

public data class GuidedNavigationTextRef(
    override val url: Url,
) : GuidedNavigationRef

public data class GuidedNavigationImageRef(
    override val url: Url,
) : GuidedNavigationRef

public data class GuidedNavigationAudioRef(
    override val url: Url,
) : GuidedNavigationRef
