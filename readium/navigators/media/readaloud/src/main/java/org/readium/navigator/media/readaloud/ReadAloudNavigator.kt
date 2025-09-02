/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(InternalReadiumApi::class)

package org.readium.navigator.media.readaloud

import kotlin.properties.Delegates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.readium.navigator.common.CssSelector
import org.readium.navigator.media.common.MediaNavigator
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.extensions.addPrefix
import org.readium.r2.shared.extensions.mapStateIn
import org.readium.r2.shared.guided.GuidedNavigationTextRef
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.ReadError

@ExperimentalReadiumApi
public class ReadAloudNavigator private constructor(
    private val guidedNavigationTree: ReadAloudNode,
    private val resources: List<ReadAloudPublication.Item>,
    audioEngineFactory: (AudioEngine.Listener) -> AudioEngine,
    ttsEngineFactory: () -> TtsEngine,
    initialSettings: ReadAloudSettings,
    initialLocation: ReadAloudGoLocation?,
) {
    public companion object {

        internal suspend operator fun invoke(
            initialLocation: ReadAloudGoLocation?,
            initialSettings: ReadAloudSettings,
            publication: ReadAloudPublication,
            audioEngineFactory: (AudioEngine.Listener) -> AudioEngine,
            ttsEngineFactory: () -> TtsEngine,
        ): ReadAloudNavigator {
            val tree = withContext(Dispatchers.Default) {
                ReadAloudNode.fromGuidedNavigationObject(publication.guidedNavigationTree)
            }

            return ReadAloudNavigator(
                guidedNavigationTree = tree,
                resources = publication.resources,
                audioEngineFactory = audioEngineFactory,
                ttsEngineFactory = ttsEngineFactory,
                initialSettings = initialSettings,
                initialLocation = initialLocation
            )
        }
    }

    public data class Playback(
        val state: State,
        val playWhenReady: Boolean,
        val node: ReadAloudNode,
        val utteranceLocation: UtteranceLocation,
    )

    public sealed interface State {

        public data object Ready : State, MediaNavigator.State.Ready

        public data object Starved : State, MediaNavigator.State.Buffering

        public data object Ended : State, MediaNavigator.State.Ended

        public data class Failure(val error: Error) : State, MediaNavigator.State.Failure
    }

    public sealed class Error(
        override val message: String,
        override val cause: org.readium.r2.shared.util.Error?,
    ) : org.readium.r2.shared.util.Error {

        public data class EngineError(override val cause: org.readium.r2.shared.util.Error) :
            Error("An error occurred in the playback engine.", cause)

        public data class ContentError(override val cause: ReadError) :
            Error("An error occurred while trying to read publication content.", cause)
    }

    private inner class AudioEngineListener : AudioEngine.Listener {

        override fun onItemChanged(engine: AudioEngine, index: Int) {
            with(stateMachine) {
                stateMutable.value = stateMutable.value.onAudioEngineItemChanged(engine, index)
            }
        }

        override fun onStateChanged(engine: AudioEngine, state: AudioEngine.State) {
            with(stateMachine) {
                stateMutable.value = stateMutable.value.onAudioEngineStateChanged(engine, state)
            }
        }
    }

    private val segmentFactory = ReadAloudSegmentFactory(
        audioEngineFactory = { audioEngineFactory(AudioEngineListener()) },
        ttsEngineFactory = ttsEngineFactory
    )

    private val dataLoader = ReadAloudDataLoader(segmentFactory, initialSettings)

    private val navigationHelper = ReadAloudNavigationHelper(initialSettings)

    private val stateMachine = ReadAloudStateMachine(dataLoader, navigationHelper)

    private val initialNode = with(navigationHelper) {
        val nodeFromLocation = initialLocation
            ?.let { guidedNavigationTree.firstMatchingLocation(it) }
            ?.firstContentNode()
        nodeFromLocation ?: guidedNavigationTree.firstContentNode() ?: guidedNavigationTree
    }

    private val stateMutable: MutableStateFlow<ReadAloudStateMachine.State> =
        MutableStateFlow(
            stateMachine.play(
                segment = dataLoader.getItemRef(initialNode)!!.segment,
                playWhenReady = true,
                settings = initialSettings
            )
        )

    private val coroutineScope: CoroutineScope =
        MainScope()

    public val playback: StateFlow<Playback> =
        stateMutable.mapStateIn(coroutineScope) { state ->
            Playback(
                playWhenReady = state.playWhenReady,
                state = state.playbackState.toState(),
                node = state.node,
                utteranceLocation = state.node.utteranceLocation
            )
        }

    private val ReadAloudNode.utteranceLocation: UtteranceLocation get() {
        val textref = refs.firstNotNullOfOrNull { it as? GuidedNavigationTextRef }
        checkNotNull(textref)
        val href = textref.url.removeFragment()
        val cssSelector = textref.url.fragment
            ?.let { fragment -> CssSelector(fragment.addPrefix("#")) }
        return UtteranceLocation(
            href = href,
            mediaType = resources.first { item -> item.href == href }.mediaType,
            cssSelector = cssSelector
        )
    }

    private fun ReadAloudStateMachine.PlaybackState.toState(): State =
        when (this) {
            is ReadAloudStateMachine.PlaybackState.Ready ->
                State.Ready
            is ReadAloudStateMachine.PlaybackState.Starved ->
                State.Starved
            is ReadAloudStateMachine.PlaybackState.Ended ->
                State.Ended
            is ReadAloudStateMachine.PlaybackState.Failure ->
                State.Failure(Error.EngineError(error))
        }

    public fun play() {
        with(stateMachine) {
            stateMutable.value = stateMutable.value.resume()
        }
    }

    public fun pause() {
        with(stateMachine) {
            stateMutable.value = stateMutable.value.pause()
        }
    }

    public fun go(node: ReadAloudNode) {
        with(stateMachine) {
            stateMutable.value = stateMutable.value.jump(node)
        }
    }

    public fun canEscape(): Boolean =
        with(navigationHelper) {
            stateMutable.value.node.isEscapable()
        }

    public fun canSkip(): Boolean =
        with(navigationHelper) {
            stateMutable.value.node.isSkippable()
        }

    public fun escape(force: Boolean = true) {
        with(navigationHelper) {
            stateMutable.value.node.escape(force)
                ?.let { go(it) }
        }
    }

    public fun skipToPrevious(force: Boolean = true) {
        with(navigationHelper) {
            stateMutable.value.node.skipToPrevious(force)
                ?.let { go(it) }
        }
    }

    public fun skipToNext(force: Boolean = true) {
        with(navigationHelper) {
            stateMutable.value.node.skipToNext(force)
                ?.let { go(it) }
        }
    }

    public val location: StateFlow<ReadAloudLocation> =
        stateMutable.mapStateIn(coroutineScope) { state ->
            val textref = state.node.refs.firstNotNullOfOrNull { it as? GuidedNavigationTextRef }
            checkNotNull(textref)
            val href = textref.url.removeFragment()
            val cssSelector = textref.url.fragment
                ?.let { fragment -> CssSelector(fragment.addPrefix("#")) }
            MediaOverlaysLocation(
                href = href,
                mediaType = resources.first { item -> item.href == href }.mediaType,
                cssSelector = cssSelector
            )
        }

    public fun goTo(location: ReadAloudGoLocation) {
        with(navigationHelper) {
            guidedNavigationTree.firstMatchingLocation(location)
                ?.let { go(it) }
        }
    }

    public fun goTo(location: ReadAloudLocation) {
        val goLocation = when (location) {
            is MediaOverlaysLocation ->
                ReadAloudGoLocation(
                    href = location.href,
                    cssSelector = location.cssSelector,
                    textAnchor = location.textAnchor
                )
            is TtsLocation ->
                throw IllegalStateException()
        }
        goTo(goLocation)
    }

    public fun goTo(url: Url) {
        val location = ReadAloudGoLocation(
            href = url.removeFragment(),
            cssSelector = url.fragment?.let { CssSelector(it.addPrefix("#")) },
            textAnchor = null
        )
        goTo(location)
    }

    public var settings: ReadAloudSettings by Delegates.observable(initialSettings) {
            property, oldValue, newValue ->
        with(stateMachine) {
            stateMutable.value = stateMutable.value.updateSettings(oldValue, newValue)
        }
    }
}
