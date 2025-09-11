/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(InternalReadiumApi::class)

package org.readium.navigator.media.readaloud

import android.os.Handler
import android.os.Looper
import kotlin.properties.Delegates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.readium.navigator.common.CssSelector
import org.readium.navigator.common.TextQuote
import org.readium.navigator.media.common.MediaNavigator
import org.readium.navigator.media.readaloud.preferences.ReadAloudSettings
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.extensions.addPrefix
import org.readium.r2.shared.extensions.mapStateIn
import org.readium.r2.shared.guided.GuidedNavigationTextRef
import org.readium.r2.shared.util.Error as BaseError
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.ReadError

@ExperimentalReadiumApi
public class ReadAloudNavigator<V : TtsVoice, E : BaseError> private constructor(
    private val guidedNavigationTree: ReadAloudNode,
    private val resources: List<ReadAloudPublication.Item>,
    private val audioEngineFactory: AudioEngineFactory,
    private val ttsEngineFactory: TtsEngineFactory<V, E>,
    initialSettings: ReadAloudSettings,
    initialLocation: ReadAloudGoLocation?,
) {
    public companion object {

        internal suspend operator fun <V : TtsVoice, E : BaseError> invoke(
            initialLocation: ReadAloudGoLocation?,
            initialSettings: ReadAloudSettings,
            publication: ReadAloudPublication,
            audioEngineFactory: AudioEngineFactory,
            ttsEngineFactory: TtsEngineFactory<V, E>,
        ): ReadAloudNavigator<V, E> {
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
        val state: ReadAloudNavigator.PlaybackState,
        val playWhenReady: Boolean,
        val node: ReadAloudNode,
        val utteranceLocation: UtteranceLocation?,
    )

    public sealed interface PlaybackState {

        public data object Ready : PlaybackState, MediaNavigator.State.Ready

        public data object Starved : PlaybackState, MediaNavigator.State.Buffering

        public data object Ended : PlaybackState, MediaNavigator.State.Ended

        public data class Failure(val error: Error) : PlaybackState, MediaNavigator.State.Failure
    }

    public sealed class Error(
        override val message: String,
        override val cause: BaseError?,
    ) : BaseError {

        public data class EngineError(override val cause: BaseError) :
            Error("An error occurred in the playback engine.", cause)

        public data class ContentError(override val cause: ReadError) :
            Error("An error occurred while trying to read publication content.", cause)
    }

    private inner class PlaybackEngineListener : PlaybackEngine.Listener {

        private val handler = Handler(Looper.getMainLooper())

        override fun onPlaybackStateChanged(state: PlaybackEngine.PlaybackState) {
            handler.post {
                with(stateMachine) {
                    stateMutable.value = stateMutable.value.onPlaybackEngineStateChanged(state)
                }
            }
        }

        override fun onStartRequested(initialState: PlaybackEngine.PlaybackState) {
            handler.post {
                with(stateMachine) {
                    stateMutable.value = stateMutable.value.onPlaybackEngineStateChanged(initialState)
                }
            }
        }

        override fun onStopRequested() {
        }

        override fun onPlaybackCompleted() {
            handler.post {
                with(stateMachine) {
                    stateMutable.value = stateMutable.value.onPlaybackCompleted()
                }
            }
        }

        override fun onRangeStarted(range: IntRange) {
            handler.post {
            }
        }
    }

    public var settings: ReadAloudSettings by Delegates.observable(initialSettings) {
            property, oldValue, newValue ->
        with(stateMachine) {
            if (newValue != oldValue) {
                stateMutable.value = stateMutable.value.onSettingsChanged(newValue)
            }
        }
    }

    public val voices: Set<V> =
        ttsEngineFactory.voices

    private val segmentFactory = ReadAloudSegmentFactory<E>(
        audioEngineFactory = { chunks: List<AudioChunk> ->
            audioEngineFactory.createPlaybackEngine(
                chunks = chunks,
                listener = PlaybackEngineListener()
            )
        },
        ttsEngineFactory = { language: Language?, utterances: List<String> ->
            val language =
                settings.language
                    .takeIf { settings.overrideContentLanguage }
                    ?: language
                    ?: settings.language

            val preferredVoiceWithRegion =
                settings.voices[language]
                    ?.let { voiceId -> ttsEngineFactory.voices.firstOrNull { it.id == voiceId } }

            val preferredVoiceWithoutRegion =
                language
                    .let { settings.voices[it.removeRegion()] }
                    ?.let { voiceId -> ttsEngineFactory.voices.firstOrNull { it.id == voiceId } }

            val fallbackVoiceWithRegion = ttsEngineFactory.voices
                .firstOrNull { language in it.languages }

            val fallbackVoiceWithoutRegion = ttsEngineFactory.voices
                .firstOrNull { voice -> language.removeRegion() in voice.languages.map { it.removeRegion() } }

            val voice = preferredVoiceWithRegion
                ?: preferredVoiceWithoutRegion
                ?: fallbackVoiceWithRegion
                ?: fallbackVoiceWithoutRegion
                ?: ttsEngineFactory.voices.firstOrNull()

            voice?.let { voice ->
                ttsEngineFactory.createPlaybackEngine(
                    voice = voice,
                    utterances = utterances,
                    listener = PlaybackEngineListener()
                )
            }
        }
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

    private val stateMutable: MutableStateFlow<ReadAloudStateMachine.State> = run {
        val itemRef = dataLoader.getItemRef(initialNode)!! // there is at least one node to read
        val nodeIndex = itemRef.nodeIndex!! // the node has content

        MutableStateFlow(
            stateMachine.play(
                segment = itemRef.segment,
                itemIndex = nodeIndex,
                playWhenReady = true,
                settings = settings
            )
        )
    }

    private val coroutineScope: CoroutineScope =
        MainScope()

    public val playback: StateFlow<Playback> =
        stateMutable.mapStateIn(coroutineScope) { state ->
            Playback(
                playWhenReady = state.playWhenReady,
                state = state.playbackState.toState(),
                node = state.node,
                utteranceLocation = state.utteranceLocation
            )
        }

    private val ReadAloudStateMachine.State.utteranceLocation: UtteranceLocation? get() =
        when (segment) {
            is AudioSegment -> {
                val textref = segment.textRefs[nodeIndex]
                val href = textref.removeFragment()
                val cssSelector = textref.fragment
                    ?.let { fragment -> CssSelector(fragment.addPrefix("#")) }
                MediaOverlaysUtteranceLocation(
                    href = href,
                    mediaType = resources.first { item -> item.href == href }.mediaType,
                    cssSelector = cssSelector
                )
            }
            is TtsSegment<*> -> null
        }

    private val ReadAloudNode.mediaOverlaysUtteranceLocation: UtteranceLocation? get() {
        val textref = refs.firstNotNullOfOrNull { it as? GuidedNavigationTextRef }
            ?: return null
        val href = textref.url.removeFragment()
        val cssSelector = textref.url.fragment
            ?.let { fragment -> CssSelector(fragment.addPrefix("#")) }
        return MediaOverlaysUtteranceLocation(
            href = href,
            mediaType = resources.first { item -> item.href == href }.mediaType,
            cssSelector = cssSelector
        )
    }

    private val ReadAloudNode.ttsUtteranceLocation: UtteranceLocation? get() {
        val textref = refs.firstNotNullOfOrNull { it as? GuidedNavigationTextRef }
            ?: return null
        val href = textref.url.removeFragment()
        val cssSelector = textref.url.fragment
            ?.let { fragment -> CssSelector(fragment.addPrefix("#")) }
        val text = text
            ?: return null
        return TtsUtteranceLocation(
            href = href,
            mediaType = resources.first { item -> item.href == href }.mediaType,
            cssSelector = cssSelector,
            // FIXME: prefix and suffix
            textQuote = TextQuote(text.plain!!, prefix = "", suffix = "")
        )
    }

    private fun ReadAloudStateMachine.PlaybackState.toState(): PlaybackState =
        when (this) {
            is ReadAloudStateMachine.PlaybackState.Ready ->
                PlaybackState.Ready
            is ReadAloudStateMachine.PlaybackState.Starved ->
                PlaybackState.Starved
            is ReadAloudStateMachine.PlaybackState.Ended ->
                PlaybackState.Ended
            is ReadAloudStateMachine.PlaybackState.Failure ->
                PlaybackState.Failure(Error.EngineError(error))
        }

    init {
        play()
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
}
