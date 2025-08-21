@file:OptIn(InternalReadiumApi::class)

package org.readium.navigator.media.readaloud

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.readium.navigator.media.common.MediaNavigator
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.extensions.mapStateIn
import org.readium.r2.shared.guided.GuidedNavigationObject
import org.readium.r2.shared.util.data.ReadError

@ExperimentalReadiumApi
public class ReadAloudNavigator private constructor(
    firstLeaf: ReadAloudLeafNode,
    audioEngineFactory: (AudioEngine.Listener) -> AudioEngine,
) {

    public companion object {

        public suspend operator fun invoke(
            guidedNavigationTree: GuidedNavigationObject,
            audioEngineFactory: (AudioEngine.Listener) -> AudioEngine,
        ): ReadAloudNavigator {
            val tree = withContext(Dispatchers.Default) {
                GuidedNavigationAdapter().adapt(guidedNavigationTree)
            }
            val firstLeaf = checkNotNull(tree.firstLeaf())
            return ReadAloudNavigator(firstLeaf, audioEngineFactory)
        }
    }

    public data class Playback(
        val state: State,
        val playWhenReady: Boolean,
        val node: ReadAloudLeafNode,
    )

    public sealed interface State {

        public data object Ready : State, MediaNavigator.State.Ready

        public data object Buffering : State, MediaNavigator.State.Buffering

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

        override fun onItemChanged(index: Int) {
            with(stateMachine) {
                stateMutable.value = stateMutable.value.onAudioEngineItemChanged(index)
            }
        }

        override fun onStateChanged(state: AudioEngine.State) {
            with(stateMachine) {
                stateMutable.value = stateMutable.value.onAudioEngineStateChanged(state)
            }
        }
    }

    private val audioEngine = audioEngineFactory(AudioEngineListener())

    private val stateMachine = ReadAloudStateMachine(audioEngine)

    private val stateMutable: MutableStateFlow<ReadAloudStateMachine.State> = run {
        val engineFood = EngineFood.AudioEngineFood.fromNode(firstLeaf)!!
        MutableStateFlow(stateMachine.play(engineFood, playWhenReady = true))
    }

    private val coroutineScope: CoroutineScope =
        MainScope()

    public val playback: StateFlow<Playback> =
        stateMutable.mapStateIn(coroutineScope) { state ->
            Playback(
                playWhenReady = state.playWhenReady,
                state = state.playbackState.toState(),
                node = state.node
            )
        }

    private fun ReadAloudStateMachine.PlaybackState.toState(): State =
        when (this) {
            is ReadAloudStateMachine.PlaybackState.Ready ->
                State.Ready
            is ReadAloudStateMachine.PlaybackState.Starved ->
                State.Buffering
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
        stateMutable.value.node.isEscapable()

    public fun canSkip(): Boolean =
        stateMutable.value.node.isSkippable()

    public fun escape(force: Boolean = true) {
        stateMutable.value.node.escape(force)
            ?.let { go(it) }
    }

    public fun skip(force: Boolean = true) {
        stateMutable.value.node.skip(force)
            ?.let { go(it) }
    }
}
