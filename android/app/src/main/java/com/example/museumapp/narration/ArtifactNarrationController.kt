package com.example.museumapp.narration

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

sealed class NarrationState {
    data object Idle : NarrationState()
    data class Speaking(val chunkIndex: Int, val totalChunks: Int) : NarrationState()
    data class Paused(val chunkIndex: Int, val totalChunks: Int) : NarrationState()
    data class Error(val message: String) : NarrationState()

    /** No installed voice for the requested locale works offline; never silently used a network voice instead. */
    data object OfflineVoiceUnavailable : NarrationState()
}

/**
 * Lifecycle-safe wrapper around [android.speech.tts.TextToSpeech]. A ViewModel owns exactly one
 * instance: call [initialize] once (e.g. from init {}) and [shutdown] from `onCleared()`. This is
 * NOT an application-wide singleton, so navigating away or switching artifacts always stops any
 * speech and releases the engine.
 *
 * Only ever selects a voice with [Voice.isNetworkConnectionRequired] == false. If no such voice
 * exists for the requested locale, [state] moves to [NarrationState.OfflineVoiceUnavailable]
 * instead of silently speaking through a network-required voice while claiming offline support.
 */
class ArtifactNarrationController {
    private val _state = MutableStateFlow<NarrationState>(NarrationState.Idle)
    val state: StateFlow<NarrationState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ready = false
    private var chunks: List<String> = emptyList()
    private var currentChunkIndex: Int = 0

    fun initialize(context: Context, locale: Locale = Locale.getDefault()) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status -> onEngineInitialized(status, locale) }
    }

    private fun onEngineInitialized(status: Int, locale: Locale) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            _state.value = NarrationState.Error("The text-to-speech engine could not be started.")
            return
        }
        val offlineVoice = selectOfflineVoice(engine, locale)
        if (offlineVoice == null) {
            _state.value = NarrationState.OfflineVoiceUnavailable
            return
        }
        engine.voice = offlineVoice
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                advanceAfterChunkFinished()
            }

            @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
            override fun onError(utteranceId: String?) {
                _state.value = NarrationState.Error("Narration could not continue.")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _state.value = NarrationState.Error("Narration could not continue.")
            }
        })
        ready = true
    }

    private fun selectOfflineVoice(engine: TextToSpeech, locale: Locale): Voice? {
        val voices = try {
            engine.voices
        } catch (_: Exception) {
            null
        }.orEmpty()
        val offlineForLocale = voices.filter { voice ->
            !voice.isNetworkConnectionRequired && voice.locale.language.equals(locale.language, ignoreCase = true)
        }
        return offlineForLocale.firstOrNull { it.locale.country.equals(locale.country, ignoreCase = true) }
            ?: offlineForLocale.firstOrNull()
    }

    /** Chunks [text] and starts speaking from the first chunk. No-op while offline-unavailable or in an error state. */
    @Synchronized
    fun play(text: String) {
        if (_state.value is NarrationState.OfflineVoiceUnavailable) return
        val engine = tts
        if (engine == null || !ready) {
            _state.value = NarrationState.Error("Narration is not ready yet.")
            return
        }
        val maxLength = (TextToSpeech.getMaxSpeechInputLength() - SAFETY_MARGIN).coerceAtLeast(MIN_CHUNK_LENGTH)
        chunks = NarrationChunker.chunk(text, maxLength)
        if (chunks.isEmpty()) return
        currentChunkIndex = 0
        speakFrom(currentChunkIndex)
    }

    /** Implemented as `tts.stop()` plus remembering the current chunk -- Android TTS has no true pause. */
    @Synchronized
    fun pause() {
        val engine = tts ?: return
        val current = _state.value
        if (current !is NarrationState.Speaking) return
        engine.stop()
        _state.value = NarrationState.Paused(current.chunkIndex, current.totalChunks)
    }

    /** Re-speaks starting from the chunk remembered by [pause]. */
    @Synchronized
    fun resume() {
        if (tts == null) return
        if (_state.value !is NarrationState.Paused) return
        speakFrom(currentChunkIndex)
    }

    /** Full stop; resets the remembered chunk back to the start. */
    @Synchronized
    fun stop() {
        tts?.stop()
        chunks = emptyList()
        currentChunkIndex = 0
        if (_state.value is NarrationState.Speaking || _state.value is NarrationState.Paused) {
            _state.value = NarrationState.Idle
        }
    }

    @Synchronized
    private fun speakFrom(index: Int) {
        val engine = tts ?: return
        if (index !in chunks.indices) {
            currentChunkIndex = 0
            _state.value = NarrationState.Idle
            return
        }
        _state.value = NarrationState.Speaking(index, chunks.size)
        engine.speak(chunks[index], TextToSpeech.QUEUE_FLUSH, null, "narration-${UUID.randomUUID()}")
    }

    @Synchronized
    private fun advanceAfterChunkFinished() {
        val nextIndex = currentChunkIndex + 1
        if (nextIndex !in chunks.indices) {
            currentChunkIndex = 0
            _state.value = NarrationState.Idle
            return
        }
        currentChunkIndex = nextIndex
        speakFrom(nextIndex)
    }

    /** Stops speech and releases the engine. Safe to call multiple times. */
    @Synchronized
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        chunks = emptyList()
        currentChunkIndex = 0
        _state.value = NarrationState.Idle
    }

    companion object {
        // Leaves headroom below TextToSpeech.getMaxSpeechInputLength() for engine-added markup.
        private const val SAFETY_MARGIN = 200
        private const val MIN_CHUNK_LENGTH = 500
    }
}
