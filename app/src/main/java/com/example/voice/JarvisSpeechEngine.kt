package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

interface SpeechEngineCallbacks {
  fun onReadyForSpeech()

  fun onRmsChanged(normalizedRms: Float)

  fun onSpeechResult(text: String)

  fun onSpeechError(errorCode: Int)

  fun onTtsStart()

  fun onTtsDone()
}

class JarvisSpeechEngine(
  context: Context,
  private val callbacks: SpeechEngineCallbacks,
) {
  private val appContext = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())
  private var speechRecognizer: SpeechRecognizer? = null
  private var tts: TextToSpeech? = null
  private var ttsReady = false
  private var listeningActive = false

  val isRecognitionAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

  init {
    tts =
      TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
          ttsReady = true
          tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
              override fun onStart(utteranceId: String?) {
                mainHandler.post { callbacks.onTtsStart() }
              }

              override fun onDone(utteranceId: String?) {
                mainHandler.post { callbacks.onTtsDone() }
              }

              @Deprecated("Deprecated in Java")
              override fun onError(utteranceId: String?) {
                mainHandler.post { callbacks.onTtsDone() }
              }

              override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post { callbacks.onTtsDone() }
              }
            },
          )
        }
      }
  }

  fun startListening(localeTag: String) {
    mainHandler.post {
      if (!isRecognitionAvailable) {
        callbacks.onSpeechError(SpeechRecognizer.ERROR_CLIENT)
        return@post
      }
      stopListeningInternal()

      val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
      speechRecognizer = recognizer
      recognizer.setRecognitionListener(
        object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {
            listeningActive = true
            callbacks.onReadyForSpeech()
          }

          override fun onBeginningOfSpeech() {}

          override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            callbacks.onRmsChanged(normalized)
          }

          override fun onBufferReceived(buffer: ByteArray?) {}

          override fun onEndOfSpeech() {
            callbacks.onRmsChanged(0f)
          }

          override fun onError(error: Int) {
            listeningActive = false
            callbacks.onRmsChanged(0f)
            callbacks.onSpeechError(error)
          }

          override fun onResults(results: Bundle?) {
            listeningActive = false
            callbacks.onRmsChanged(0f)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val top = matches?.firstOrNull()?.trim().orEmpty()
            if (top.isEmpty()) {
              callbacks.onSpeechError(SpeechRecognizer.ERROR_NO_MATCH)
            } else {
              callbacks.onSpeechResult(top)
            }
          }

          override fun onPartialResults(partialResults: Bundle?) {}

          override fun onEvent(eventType: Int, params: Bundle?) {}
        },
      )

      val intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
          putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

      try {
        recognizer.startListening(intent)
      } catch (_: Exception) {
        listeningActive = false
        callbacks.onSpeechError(SpeechRecognizer.ERROR_CLIENT)
      }
    }
  }

  fun stopListening() {
    mainHandler.post { stopListeningInternal() }
  }

  private fun stopListeningInternal() {
    listeningActive = false
    try {
      speechRecognizer?.cancel()
      speechRecognizer?.destroy()
    } catch (_: Exception) {
    }
    speechRecognizer = null
  }

  fun speak(text: String, localeTag: String) {
    mainHandler.post {
      // Always stop SpeechRecognizer before TTS speaks so Jarvis never hears itself
      stopListeningInternal()

      val engine = tts
      if (!ttsReady || engine == null) {
        callbacks.onTtsDone()
        return@post
      }

      val targetLocale = Locale.forLanguageTag(localeTag)
      val availability = engine.isLanguageAvailable(targetLocale)
      if (availability >= TextToSpeech.LANG_AVAILABLE) {
        engine.language = targetLocale
      } else {
        engine.language = Locale.forLanguageTag("en-IN")
      }

      val utteranceId = UUID.randomUUID().toString()
      engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
  }

  fun stopSpeaking() {
    mainHandler.post {
      try {
        tts?.stop()
      } catch (_: Exception) {
      }
    }
  }

  fun release() {
    mainHandler.post {
      stopListeningInternal()
      try {
        tts?.stop()
        tts?.shutdown()
      } catch (_: Exception) {
      }
      tts = null
    }
  }
}
