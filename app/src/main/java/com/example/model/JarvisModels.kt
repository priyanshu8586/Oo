package com.example.model

enum class JarvisState {
  IDLE,
  WAKE_LISTENING,
  LISTENING,
  THINKING,
  SPEAKING,
}

enum class RecognitionLanguageMode(val label: String, val localeTag: String) {
  AUTO("AUTO", "hi-IN"),
  HINDI("HI", "hi-IN"),
  ENGLISH("EN", "en-IN"),
}

enum class SpeakerRole {
  USER,
  JARVIS,
}

data class TranscriptEntry(
  val id: Long,
  val role: SpeakerRole,
  val text: String,
)

data class ChatMessage(
  val role: String, // "user" or "model"
  val text: String,
)

data class JarvisUiState(
  val hasApiKey: Boolean = false,
  val showSetupScreen: Boolean = true,
  val allowCancelSetup: Boolean = false,
  val savedKeyPreview: String = "",
  val jarvisState: JarvisState = JarvisState.IDLE,
  val languageMode: RecognitionLanguageMode = RecognitionLanguageMode.AUTO,
  val activeLocaleTag: String = "hi-IN",
  val alwaysListeningWake: Boolean = false,
  val rmsLevel: Float = 0f,
  val timerSecondsRemaining: Int = 0,
  val transcript: List<TranscriptEntry> = emptyList(),
  val statusDetail: String = "Supports Hindi, English & Hinglish",
  val speechAvailable: Boolean = true,
)
