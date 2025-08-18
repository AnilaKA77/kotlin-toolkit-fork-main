
package org.readium.navigator.media.readaloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.readium.navigator.media.common.MediaNavigator
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.guided.GuidedNavigationContainer
import org.readium.r2.shared.util.data.ReadError

@ExperimentalReadiumApi
public class ReadAloudNavigator private constructor(
    firstLeaf: ReadAloudLeafNode,
    audioEngineFactory: (AudioEngine.Listener) -> AudioEngine,
) {

    public companion object {

        public suspend operator fun invoke(
            guidedNavigationTree: GuidedNavigationContainer,
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
    )

    public sealed interface State {

        public data object Ready : State, MediaNavigator.State.Ready

        public data object Buffering : MediaNavigator.State.Buffering

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
            val state = stateMutable.value as ReadAloudStateMachine.State.Playing
            val engineFood = state.engineFood as EngineFood.AudioEngineFood
            nodeMutable.value = engineFood.nodes[index]
        }

        override fun onPlaybackEnded() {
            with(stateMachine) {
                stateMutable.value = stateMutable.value.onAudioEngineEnded()
            }
        }
    }

    private val audioEngine = audioEngineFactory(AudioEngineListener())

    private val stateMachine = ReadAloudStateMachine(audioEngine)

    private val stateMutable: MutableStateFlow<ReadAloudStateMachine.State> =
        MutableStateFlow(stateMachine.start(firstLeaf, paused = false))

    private val playbackMutable: MutableStateFlow<Playback> =
        MutableStateFlow(Playback(state = State.Ready, playWhenReady = true))

    private val nodeMutable: MutableStateFlow<ReadAloudNode> =
        MutableStateFlow(firstLeaf)

    public val playback: StateFlow<Playback> =
        playbackMutable.asStateFlow()

    public val node: StateFlow<ReadAloudNode> =
        nodeMutable.asStateFlow()

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
        nodeMutable.value.isEscapable()

    public fun canSkip(): Boolean =
        nodeMutable.value.isSkippable()

    public fun escape(force: Boolean = true) {
        nodeMutable.value.escape(force)
            ?.let { go(it) }
    }

    public fun skip(force: Boolean = true) {
        nodeMutable.value.skip(force)
            ?.let { go(it) }
    }
}
