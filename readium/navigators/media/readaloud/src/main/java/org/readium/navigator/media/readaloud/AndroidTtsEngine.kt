/*
 * Copyright 2022 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(InternalReadiumApi::class)

package org.readium.navigator.media.readaloud

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.*
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice as AndroidVoice
import android.speech.tts.Voice.*
import java.util.UUID
import kotlin.collections.orEmpty
import kotlinx.coroutines.*
import org.readium.navigator.media.readaloud.AndroidTtsEngine.Companion.initializeTextToSpeech
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.extensions.tryOrNull
import org.readium.r2.shared.util.Language

@ExperimentalReadiumApi
public class AndroidTtsEngineProvider private constructor(
    private val context: Context,
    private val textToSpeech: TextToSpeech,
) : TtsEngineProvider<AndroidTtsEngine.Voice, AndroidTtsEngine.Error> {

    override val voices: Set<AndroidTtsEngine.Voice> =
        tryOrNull { textToSpeech.voices } // throws on Nexus 4
            ?.map<AndroidVoice, AndroidTtsEngine.Voice> { it.toTtsEngineVoice() }
            ?.toSet()
            .orEmpty()

    override fun createEngine(
        voice: AndroidTtsEngine.Voice,
        utterances: List<String>,
        listener: TtsEngine.Listener<AndroidTtsEngine.Error>,
    ): TtsEngine {
        val voice = voices.firstOrNull { it.name == voice.name }
        checkNotNull(voice)

        return AndroidTtsEngine(
            context = context,
            engine = textToSpeech,
            listener = listener,
            voice = voice,
            utterances = utterances
        )
    }

    public companion object {

        public suspend operator fun invoke(
            context: Context,
        ): AndroidTtsEngineProvider? {
            val textToSpeech = initializeTextToSpeech(context)
                ?: return null

            return AndroidTtsEngineProvider(context, textToSpeech)
        }

        private fun AndroidVoice.toTtsEngineVoice() =
            AndroidTtsEngine.Voice(
                name = name,
                language = Language(locale),
                quality = when (quality) {
                    QUALITY_VERY_HIGH -> AndroidTtsEngine.Voice.Quality.Highest
                    QUALITY_HIGH -> AndroidTtsEngine.Voice.Quality.High
                    QUALITY_NORMAL -> AndroidTtsEngine.Voice.Quality.Normal
                    QUALITY_LOW -> AndroidTtsEngine.Voice.Quality.Low
                    QUALITY_VERY_LOW -> AndroidTtsEngine.Voice.Quality.Lowest
                    else -> throw IllegalStateException("Unexpected voice quality.")
                },
                requiresNetwork = isNetworkConnectionRequired
            )
    }
}

/*
 * On some Android implementations (i.e. on Oppo A9 2020 running Android 11),
 * the TextToSpeech instance is often disconnected from the underlying service when the playback
 * is paused and the app moves to the background. So we try to reset the TextToSpeech before
 * actually returning an error. In the meantime, requests to the engine are queued
 * into [pendingRequests].
 */

/**
 * Default [TtsEngine] implementation using Android's native text to speech engine.
 */
@ExperimentalReadiumApi
public class AndroidTtsEngine internal constructor(
    private val context: Context,
    engine: TextToSpeech,
    private val listener: TtsEngine.Listener<Error>,
    public val voice: Voice,
    override val utterances: List<String>,
) : TtsEngine {

    public companion object {

        /**
         * Starts the activity to install additional voice data.
         */
        @SuppressLint("QueryPermissionsNeeded")
        public fun requestInstallVoice(context: Context) {
            val intent = Intent()
                .setAction(Engine.ACTION_INSTALL_TTS_DATA)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val availableActivities =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.queryIntentActivities(
                        intent,
                        PackageManager.ResolveInfoFlags.of(0)
                    )
                } else {
                    context.packageManager.queryIntentActivities(intent, 0)
                }

            if (availableActivities.isNotEmpty()) {
                context.startActivity(intent)
            }
        }

        internal suspend fun initializeTextToSpeech(
            context: Context,
        ): TextToSpeech? {
            val init = CompletableDeferred<Boolean>()

            val initListener = OnInitListener { status ->
                init.complete(status == SUCCESS)
            }
            val engine = TextToSpeech(context, initListener)
            return if (init.await()) engine else null
        }
    }

    public sealed class Error(
        override val message: String,
        override val cause: org.readium.r2.shared.util.Error? = null,
    ) : org.readium.r2.shared.util.Error {

        /** Denotes a generic operation failure. */
        public data object Unknown : Error("An unknown error occurred.")

        /** Denotes a failure caused by an invalid request. */
        public data object InvalidRequest : Error("Invalid request")

        /** Denotes a failure caused by a network connectivity problems. */
        public data object Network : Error("A network error occurred.")

        /** Denotes a failure caused by network timeout. */
        public data object NetworkTimeout : Error("Network timeout")

        /** Denotes a failure caused by an unfinished download of the voice data. */
        public data object NotInstalledYet : Error("Voice not installed yet.")

        /** Denotes a failure related to the output (audio device or a file). */
        public data object Output : Error("An error related to the output occurred.")

        /** Denotes a failure of a TTS service. */
        public data object Service : Error("An error occurred with the TTS service.")

        /** Denotes a failure of a TTS engine to synthesize the given input. */
        public data object Synthesis : Error("Synthesis failed.")

        /**
         * Denotes the language data is missing.
         *
         * You can open the Android settings to install the missing data with:
         * AndroidTtsEngine.requestInstallVoice(context)
         */
        public data class LanguageMissingData(val language: Language) :
            Error("Language data is missing.")

        /**
         * Android's TTS error code.
         * See https://developer.android.com/reference/android/speech/tts/TextToSpeech#ERROR
         */
        public companion object {
            internal fun fromNativeError(code: Int): Error =
                when (code) {
                    ERROR_INVALID_REQUEST -> InvalidRequest
                    ERROR_NETWORK -> Network
                    ERROR_NETWORK_TIMEOUT -> NetworkTimeout
                    ERROR_NOT_INSTALLED_YET -> NotInstalledYet
                    ERROR_OUTPUT -> Output
                    ERROR_SERVICE -> Service
                    ERROR_SYNTHESIS -> Synthesis
                    else -> Unknown
                }
        }
    }

    /**
     * Represents a voice provided by the TTS engine which can speak an utterance.
     *
     * @param name Voice name
     * @param language Language (and region) this voice belongs to.
     * @param quality Voice quality.
     * @param requiresNetwork Indicates whether using this voice requires an Internet connection.
     */
    public data class Voice(
        val name: String,
        val language: Language,
        val quality: Quality = Quality.Normal,
        val requiresNetwork: Boolean = false,
    ) : TtsVoice {

        override val id: TtsVoice.Id =
            TtsVoice.Id("${AndroidTtsEngine::class.qualifiedName}-$name}")

        override val languages: Set<Language> =
            setOf(language)

        public enum class Quality {
            Lowest,
            Low,
            Normal,
            High,
            Highest,
        }
    }

    private data class Request(
        val id: Id,
        val text: String,
    ) {

        @JvmInline
        value class Id(val value: String)
    }

    private sealed class State {

        data class EngineAvailable(
            val engine: TextToSpeech,
        ) : State()

        data class WaitingForService(
            var pendingRequest: Request?,
        ) : State()

        data class Failure(
            val error: Error,
        ) : State()
    }

    private val coroutineScope: CoroutineScope =
        MainScope()

    private var state: State =
        State.EngineAvailable(engine)

    private var isPrepared: Boolean =
        false

    private var isClosed: Boolean =
        false

    private var speed: Double = 1.0

    private var pitch: Double = 1.0

    override fun prepare() {
        if (isPrepared) {
            return
        }

        isPrepared = true

        (state as? State.EngineAvailable)
            ?.let { setupListener(it.engine) }
        listener.onReady()
    }

    override fun speak(
        utteranceIndex: Int,
    ) {
        check(isPrepared) { "Engine has not been prepared." }
        check(!isClosed) { "Engine is closed." }

        val id = Request.Id(UUID.randomUUID().toString())
        val text = checkNotNull(utterances)[utteranceIndex]
        val request = Request(id, text)
        when (val stateNow = state) {
            is State.WaitingForService -> {
                stateNow.pendingRequest = request
            }
            is State.Failure -> {
                tryReconnect(request)
            }
            is State.EngineAvailable -> {
                if (!doSpeak(stateNow.engine, request)) {
                    cleanEngine(stateNow.engine)
                    tryReconnect(request)
                }
            }
        }
    }

    public override fun stop() {
        when (val stateNow = state) {
            is State.EngineAvailable -> {
                stateNow.engine.stop()
            }
            is State.Failure -> {
                // Do nothing
            }
            is State.WaitingForService -> {
                stateNow.pendingRequest = null
            }
        }
    }

    public override fun release() {
        if (isClosed) {
            return
        }

        isClosed = true
        coroutineScope.cancel()

        when (val stateNow = state) {
            is State.EngineAvailable -> {
                cleanEngine(stateNow.engine)
            }
            is State.Failure -> {
                // Do nothing
            }
            is State.WaitingForService -> {
                // Do nothing
            }
        }
    }

    private fun doSpeak(
        engine: TextToSpeech,
        request: Request,
    ): Boolean {
        engine.setupPitchAndSpeed()
        return engine.setupVoice() &&
            (engine.speak(request.text, QUEUE_ADD, null, request.id.value) == SUCCESS)
    }

    private fun setupListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(UtteranceListener(listener))
    }

    private fun onReconnectionSucceeded(engine: TextToSpeech) {
        val previousState = state as State.WaitingForService
        setupListener(engine)
        engine.setupPitchAndSpeed()
        state = State.EngineAvailable(engine)
        if (isClosed) {
            engine.shutdown()
        } else {
            previousState.pendingRequest?.let { doSpeak(engine, it) }
        }
    }

    private fun onReconnectionFailed() {
        val error = Error.Service
        state = State.Failure(error)
        listener.onError(error)
    }

    private fun tryReconnect(request: Request) {
        state = State.WaitingForService(request)
        coroutineScope.launch {
            initializeTextToSpeech(context)
                ?.let { onReconnectionSucceeded(it) }
                ?: onReconnectionFailed()
        }
    }

    private fun cleanEngine(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(null)
        engine.shutdown()
    }

    private fun TextToSpeech.setupPitchAndSpeed() {
        setSpeechRate(speed.toFloat())
        setPitch(pitch.toFloat())
    }

    private fun TextToSpeech.setupVoice(): Boolean {
        val voice = voiceForName(voice.name)
        setVoice(voice)
        return true
    }

    private fun TextToSpeech.voiceForName(name: String) =
        voices.firstOrNull { it.name == name }

    private class UtteranceListener(
        private val listener: TtsEngine.Listener<Error>,
    ) : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
        }

        override fun onStop(utteranceId: String, interrupted: Boolean) {
            listener.onInterrupted()
        }

        override fun onDone(utteranceId: String) {
            listener.onDone()
        }

        @Deprecated(
            "Deprecated in the interface",
            ReplaceWith("onError(utteranceId, -1)"),
            level = DeprecationLevel.ERROR
        )
        override fun onError(utteranceId: String) {
            onError(utteranceId, -1)
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            listener.onError(Error.fromNativeError(errorCode))
        }

        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
            listener.onRange(start until end)
        }
    }
}
