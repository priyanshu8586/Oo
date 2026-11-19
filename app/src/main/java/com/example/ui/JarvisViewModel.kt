package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiClient
import com.example.data.GeminiResult
import com.example.model.ChatMessage
import com.example.model.JarvisState
import com.example.model.JarvisUiState
import com.example.model.RecognitionLanguageMode
import com.example.model.SpeakerRole
import com.example.model.TranscriptEntry
import com.example.voice.CommandProcessor
import com.example.voice.JarvisSpeechEngine
import com.example.voice.LanguageDetector
import com.example.voice.LocalCommandAction
import com.example.voice.SpeechEngineCallbacks
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class JarvisViewModel(application: Application) : AndroidViewModel(application) {
  companion object {
    private const val PREFS_NAME = "jarvis_prefs"
    private const val KEY_GEMINI_API = "gemini_api_key"
    private const val MAX_MEMORY = 10
  }

  private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val geminiClient = GeminiClient()
  private val history = ArrayDeque<ChatMessage>()
  private var nextEntryId = 1L
  private var lastJarvisReply: String = ""
  private var sessionActive = false
  private var triedFallbackLocale = false
  private var timerJob: Job? = null

  private val _uiState = MutableStateFlow(JarvisUiState())
  val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

  private val speechCallbacks =
    object : SpeechEngineCallbacks {
      override fun onReadyForSpeech() {
        _uiState.update { state ->
          val nextMode =
            if (state.alwaysListeningWake && !sessionActive) {
              JarvisState.WAKE_LISTENING
            } else {
              JarvisState.LISTENING
            }
          state.copy(
            jarvisState = nextMode,
            statusDetail =
              if (nextMode == JarvisState.WAKE_LISTENING) {
                "Say \"Jarvis\" followed by your command"
              } else {
                "Mic active (${state.activeLocaleTag})"
              },
          )
        }
      }

      override fun onRmsChanged(normalizedRms: Float) {
        _uiState.update { it.copy(rmsLevel = normalizedRms) }
      }

      override fun onSpeechResult(text: String) {
        handleRecognizedSpeech(text)
      }

      override fun onSpeechError(errorCode: Int) {
        handleRecognitionError(errorCode)
      }

      override fun onTtsStart() {
        _uiState.update {
          it.copy(
            jarvisState = JarvisState.SPEAKING,
            statusDetail = "Mic paused while Jarvis speaks",
            rmsLevel = 0f,
          )
        }
      }

      override fun onTtsDone() {
        onSpeakingFinished()
      }
    }

  private val speechEngine = JarvisSpeechEngine(application, speechCallbacks)

  init {
    val savedKey = prefs.getString(KEY_GEMINI_API, "").orEmpty().trim()
    _uiState.update {
      it.copy(
        hasApiKey = savedKey.isNotEmpty(),
        showSetupScreen = savedKey.isEmpty(),
        allowCancelSetup = false,
        savedKeyPreview = maskKey(savedKey),
        speechAvailable = speechEngine.isRecognitionAvailable,
      )
    }
  }

  fun saveApiKey(rawKey: String): Boolean {
    val trimmed = rawKey.trim()
    if (trimmed.length < 15) return false
    prefs.edit().putString(KEY_GEMINI_API, trimmed).apply()
    _uiState.update {
      it.copy(
        hasApiKey = true,
        showSetupScreen = false,
        allowCancelSetup = true,
        savedKeyPreview = maskKey(trimmed),
      )
    }
    return true
  }

  fun openKeySetup() {
    stopAllVoice()
    _uiState.update {
      it.copy(
        showSetupScreen = true,
        allowCancelSetup = it.hasApiKey,
      )
    }
  }

  fun cancelKeySetup() {
    if (_uiState.value.hasApiKey) {
      _uiState.update { it.copy(showSetupScreen = false) }
    }
  }

  fun clearTranscript() {
    history.clear()
    _uiState.update { it.copy(transcript = emptyList()) }
  }

  fun cycleLanguageMode(announce: Boolean = false) {
    val next =
      when (_uiState.value.languageMode) {
        RecognitionLanguageMode.AUTO -> RecognitionLanguageMode.HINDI
        RecognitionLanguageMode.HINDI -> RecognitionLanguageMode.ENGLISH
        RecognitionLanguageMode.ENGLISH -> RecognitionLanguageMode.AUTO
      }
    triedFallbackLocale = false
    _uiState.update {
      it.copy(
        languageMode = next,
        activeLocaleTag = next.localeTag,
      )
    }
    if (announce) {
      val msg =
        when (next) {
          RecognitionLanguageMode.HINDI -> "अब मैं हिंदी में सुनूँगा।"
          RecognitionLanguageMode.ENGLISH -> "Switched to English."
          RecognitionLanguageMode.AUTO -> "Language set to automatic Hindi and English."
        }
      speakJarvis(msg, if (next == RecognitionLanguageMode.HINDI) "hi-IN" else "en-IN")
    }
  }

  fun toggleAlwaysListening(enabled: Boolean) {
    _uiState.update { it.copy(alwaysListeningWake = enabled) }
    if (enabled) {
      sessionActive = false
      if (_uiState.value.jarvisState != JarvisState.SPEAKING) {
        startRecognition()
      }
    } else {
      stopAllVoice()
    }
  }

  fun onReactorTapped() {
    val current = _uiState.value.jarvisState
    if (current == JarvisState.SPEAKING) {
      speechEngine.stopSpeaking()
      sessionActive = true
      startRecognition()
      return
    }
    if (current == JarvisState.LISTENING && sessionActive) {
      stopAllVoice()
      return
    }
    sessionActive = true
    triedFallbackLocale = false
    startRecognition()
  }

  fun onPermissionDenied() {
    sessionActive = false
    _uiState.update {
      it.copy(
        alwaysListeningWake = false,
        jarvisState = JarvisState.IDLE,
        statusDetail = "Microphone permission is required for voice commands",
      )
    }
    speakJarvis("Microphone permission is required to hear your commands.", "en-IN")
  }

  private fun startRecognition() {
    if (!speechEngine.isRecognitionAvailable) {
      _uiState.update {
        it.copy(
          speechAvailable = false,
          jarvisState = JarvisState.IDLE,
          statusDetail = "Google Speech Recognition service is unavailable on this device.",
        )
      }
      return
    }
    val mode = _uiState.value.languageMode
    val locale =
      when (mode) {
        RecognitionLanguageMode.AUTO -> if (triedFallbackLocale) "en-IN" else "hi-IN"
        RecognitionLanguageMode.HINDI -> "hi-IN"
        RecognitionLanguageMode.ENGLISH -> "en-IN"
      }
    _uiState.update { it.copy(activeLocaleTag = locale) }
    speechEngine.startListening(locale)
  }

  private fun handleRecognizedSpeech(rawText: String) {
    val state = _uiState.value
    if (state.alwaysListeningWake && !sessionActive) {
      val wakeRegex = Regex("\\b(jarvis|जार्विस)\\b", RegexOption.IGNORE_CASE)
      if (!wakeRegex.containsMatchIn(rawText)) {
        viewModelScope.launch {
          delay(250)
          if (_uiState.value.alwaysListeningWake && !sessionActive) {
            startRecognition()
          }
        }
        return
      }
      val stripped = rawText.replace(Regex("^.*?\\b(jarvis|जार्विस)\\b[,:\\s]*", RegexOption.IGNORE_CASE), "").trim()
      sessionActive = true
      if (stripped.isEmpty()) {
        speakJarvis("Yes, I am listening.", "en-IN")
        return
      }
      processCommand(stripped)
      return
    }

    processCommand(rawText)
  }

  private fun processCommand(text: String) {
    triedFallbackLocale = false
    appendTranscript(SpeakerRole.USER, text)

    when (val action = CommandProcessor.match(text)) {
      is LocalCommandAction.Stop -> {
        stopAllVoice()
        appendTranscript(SpeakerRole.JARVIS, action.spokenReply)
      }
      is LocalCommandAction.Repeat -> {
        if (lastJarvisReply.isNotEmpty()) {
          speakJarvis(lastJarvisReply, LanguageDetector.detectLocaleTag(lastJarvisReply))
        } else {
          val msg =
            if (action.localeTag == "hi-IN") "अभी दोहराने के लिए कुछ नहीं है।"
            else "Nothing to repeat yet."
          speakJarvis(msg, action.localeTag)
        }
      }
      LocalCommandAction.ChangeLanguage -> {
        cycleLanguageMode(announce = true)
      }
      is LocalCommandAction.SpeakOnly -> {
        speakJarvis(action.spokenReply, action.localeTag)
      }
      is LocalCommandAction.OpenUrl -> {
        launchExternalTarget(action.url, action.packageName)
        speakJarvis(action.spokenReply, action.localeTag)
      }
      is LocalCommandAction.StartTimer -> {
        startCountdownTimer(action.minutes, action.localeTag)
        speakJarvis(action.spokenReply, action.localeTag)
      }
      LocalCommandAction.NotMatched -> {
        queryGemini(text)
      }
    }
  }

  private fun queryGemini(userText: String) {
    if (!isNetworkConnected()) {
      speakJarvis("No internet connection. Please check your network and try again.", "en-IN")
      return
    }

    val apiKey = prefs.getString(KEY_GEMINI_API, "").orEmpty().trim()
    if (apiKey.isEmpty()) {
      openKeySetup()
      return
    }

    _uiState.update {
      it.copy(
        jarvisState = JarvisState.THINKING,
        statusDetail = "Thinking…",
        rmsLevel = 0f,
      )
    }

    history.addLast(ChatMessage(role = "user", text = userText))
    while (history.size > MAX_MEMORY) {
      history.removeFirst()
    }

    viewModelScope.launch {
      when (val result = geminiClient.generateReply(apiKey, history.toList())) {
        is GeminiResult.Success -> {
          history.addLast(ChatMessage(role = "model", text = result.text))
          while (history.size > MAX_MEMORY) {
            history.removeFirst()
          }
          speakJarvis(result.text, LanguageDetector.detectLocaleTag(result.text))
        }
        is GeminiResult.Error -> {
          if (history.isNotEmpty() && history.last().role == "user") {
            history.removeLast()
          }
          speakJarvis(result.spokenMessage, "en-IN")
        }
      }
    }
  }

  private fun handleRecognitionError(errorCode: Int) {
    val state = _uiState.value
    when (errorCode) {
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
        onPermissionDenied()
      }
      SpeechRecognizer.ERROR_NETWORK,
      SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
        speakJarvis("Speech recognition needs an internet connection.", "en-IN")
      }
      SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
      SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
        if (state.languageMode == RecognitionLanguageMode.AUTO && !triedFallbackLocale) {
          triedFallbackLocale = true
          viewModelScope.launch {
            delay(200)
            startRecognition()
          }
        } else {
          speakJarvis("Selected speech language is unavailable on this device.", "en-IN")
        }
      }
      SpeechRecognizer.ERROR_NO_MATCH,
      SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
        if (state.alwaysListeningWake && !sessionActive) {
          viewModelScope.launch {
            delay(300)
            if (_uiState.value.alwaysListeningWake && !sessionActive) {
              startRecognition()
            }
          }
        } else if (state.languageMode == RecognitionLanguageMode.AUTO && !triedFallbackLocale) {
          triedFallbackLocale = true
          viewModelScope.launch {
            delay(250)
            startRecognition()
          }
        } else {
          triedFallbackLocale = false
          speakJarvis("I did not catch that. Please say it again.", "en-IN")
        }
      }
      else -> {
        if (sessionActive || state.alwaysListeningWake) {
          viewModelScope.launch {
            delay(450)
            if ((sessionActive || _uiState.value.alwaysListeningWake) &&
              _uiState.value.jarvisState != JarvisState.SPEAKING
            ) {
              startRecognition()
            }
          }
        } else {
          _uiState.update {
            it.copy(
              jarvisState = JarvisState.IDLE,
              statusDetail = "Supports Hindi, English & Hinglish",
            )
          }
        }
      }
    }
  }

  private fun speakJarvis(text: String, localeTag: String) {
    lastJarvisReply = text
    appendTranscript(SpeakerRole.JARVIS, text)
    _uiState.update {
      it.copy(
        jarvisState = JarvisState.SPEAKING,
        statusDetail = "Mic paused while Jarvis speaks",
      )
    }
    speechEngine.speak(text, localeTag)
  }

  private fun onSpeakingFinished() {
    val state = _uiState.value
    if (sessionActive || state.alwaysListeningWake) {
      viewModelScope.launch {
        delay(350)
        if ((sessionActive || _uiState.value.alwaysListeningWake) &&
          !_uiState.value.showSetupScreen
        ) {
          startRecognition()
        } else {
          _uiState.update {
            it.copy(
              jarvisState = JarvisState.IDLE,
              statusDetail = "Supports Hindi, English & Hinglish",
            )
          }
        }
      }
    } else {
      _uiState.update {
        it.copy(
          jarvisState = JarvisState.IDLE,
          statusDetail = "Supports Hindi, English & Hinglish",
        )
      }
    }
  }

  private fun startCountdownTimer(minutes: Double, localeTag: String) {
    timerJob?.cancel()
    val totalSeconds = (minutes * 60.0).toInt().coerceAtLeast(1)
    timerJob =
      viewModelScope.launch {
        for (remaining in totalSeconds downTo 1) {
          _uiState.update { it.copy(timerSecondsRemaining = remaining) }
          delay(1000L)
        }
        _uiState.update { it.copy(timerSecondsRemaining = 0) }
        val doneMsg =
          if (localeTag == "hi-IN") {
            "आपका टाइमर पूरा हो गया है।"
          } else {
            "Your timer is complete."
          }
        speakJarvis(doneMsg, localeTag)
      }
  }

  private fun launchExternalTarget(url: String, packageName: String?) {
    val context = getApplication<Application>()
    val pm = context.packageManager
    val packageIntent = packageName?.let { pm.getLaunchIntentForPackage(it) }
    val intent =
      (packageIntent ?: Intent(Intent.ACTION_VIEW, Uri.parse(url))).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    try {
      context.startActivity(intent)
    } catch (_: Exception) {
    }
  }

  private fun stopAllVoice() {
    sessionActive = false
    speechEngine.stopListening()
    speechEngine.stopSpeaking()
    _uiState.update {
      it.copy(
        jarvisState = if (it.alwaysListeningWake) JarvisState.WAKE_LISTENING else JarvisState.IDLE,
        rmsLevel = 0f,
        statusDetail =
          if (it.alwaysListeningWake) "Say \"Jarvis\" followed by your command"
          else "Supports Hindi, English & Hinglish",
      )
    }
  }

  private fun appendTranscript(role: SpeakerRole, text: String) {
    val entry = TranscriptEntry(id = nextEntryId++, role = role, text = text)
    _uiState.update { it.copy(transcript = (it.transcript + entry).takeLast(50)) }
  }

  private fun isNetworkConnected(): Boolean {
    val cm =
      getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE)
        as? ConnectivityManager ?: return true
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  private fun maskKey(key: String): String {
    if (key.length <= 8) return ""
    return "${key.take(6)}…${key.takeLast(4)}"
  }

  override fun onCleared() {
    super.onCleared()
    timerJob?.cancel()
    speechEngine.release()
  }
}
