package com.example.voice

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class LocalCommandAction {
  data class Stop(val spokenReply: String, val localeTag: String) : LocalCommandAction()

  data class Repeat(val localeTag: String) : LocalCommandAction()

  data object ChangeLanguage : LocalCommandAction()

  data class SpeakOnly(val spokenReply: String, val localeTag: String) : LocalCommandAction()

  data class OpenUrl(
    val url: String,
    val packageName: String?,
    val spokenReply: String,
    val localeTag: String,
  ) : LocalCommandAction()

  data class StartTimer(val minutes: Double, val spokenReply: String, val localeTag: String) :
    LocalCommandAction()

  data object NotMatched : LocalCommandAction()
}

object CommandProcessor {
  private val stopRegex =
    Regex("^(stop|ruko|band karo|quiet|silence|चुप|रुको|बंद करो)\\b", RegexOption.IGNORE_CASE)
  private val repeatRegex =
    Regex("(repeat that|say that again|repeat|dobara bolo|phir se bolo|दोबारा बोलो|फिर से)", RegexOption.IGNORE_CASE)
  private val langRegex =
    Regex("(change language|switch language|bhasha badlo|भाषा बदलो)", RegexOption.IGNORE_CASE)
  private val timeRegex =
    Regex("(what'?s the time|what is the time|tell me the time|current time|kitne baje|kya samay|टाइम|समय)", RegexOption.IGNORE_CASE)
  private val openVerbRegex = Regex("(open|kholo|खोलो)", RegexOption.IGNORE_CASE)
  private val timerRegex = Regex("(timer|टाइमर)", RegexOption.IGNORE_CASE)
  private val minuteRegex =
    Regex("(\\d+(?:\\.\\d+)?)\\s*(?:minute|minutes|min|mins|मिनट)", RegexOption.IGNORE_CASE)

  private val numberWords =
    mapOf(
      "one" to 1.0,
      "two" to 2.0,
      "three" to 3.0,
      "four" to 4.0,
      "five" to 5.0,
      "six" to 6.0,
      "seven" to 7.0,
      "eight" to 8.0,
      "nine" to 9.0,
      "ten" to 10.0,
      "ek" to 1.0,
      "do" to 2.0,
      "teen" to 3.0,
      "char" to 4.0,
      "paanch" to 5.0,
      "एक" to 1.0,
      "दो" to 2.0,
      "तीन" to 3.0,
      "चार" to 4.0,
      "पांच" to 5.0,
    )

  fun match(rawText: String): LocalCommandAction {
    val trimmed = rawText.trim()
    val lower = trimmed.lowercase(Locale.ROOT)
    val lang = LanguageDetector.detectLocaleTag(trimmed)

    if (stopRegex.containsMatchIn(lower) || lower == "stop listening") {
      val msg = if (lang == "hi-IN") "ठीक है, मैं रुक गया हूँ।" else "Okay, standing by."
      return LocalCommandAction.Stop(msg, lang)
    }

    if (repeatRegex.containsMatchIn(lower)) {
      return LocalCommandAction.Repeat(lang)
    }

    if (langRegex.containsMatchIn(lower)) {
      return LocalCommandAction.ChangeLanguage
    }

    if (timeRegex.containsMatchIn(lower)) {
      val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
      val timeStr = formatter.format(Date())
      val reply = if (lang == "hi-IN") "अभी $timeStr बजे हैं।" else "It is $timeStr."
      return LocalCommandAction.SpeakOnly(reply, lang)
    }

    if (openVerbRegex.containsMatchIn(lower)) {
      when {
        lower.contains("youtube") || lower.contains("यूट्यूब") -> {
          val reply = if (lang == "hi-IN") "यूट्यूब खोल रहा हूँ।" else "Opening YouTube."
          return LocalCommandAction.OpenUrl(
            "https://www.youtube.com",
            "com.google.android.youtube",
            reply,
            lang,
          )
        }
        lower.contains("google") || lower.contains("गूगल") -> {
          val reply = if (lang == "hi-IN") "गूगल खोल रहा हूँ।" else "Opening Google."
          return LocalCommandAction.OpenUrl(
            "https://www.google.com",
            "com.google.android.googlequicksearchbox",
            reply,
            lang,
          )
        }
        lower.contains("whatsapp") || lower.contains("व्हाट्सएप") || lower.contains("व्हाट्सऐप") -> {
          val reply = if (lang == "hi-IN") "व्हाट्सऐप खोल रहा हूँ।" else "Opening WhatsApp."
          return LocalCommandAction.OpenUrl("https://wa.me/", "com.whatsapp", reply, lang)
        }
      }
    }

    if (timerRegex.containsMatchIn(lower)) {
      val minutes = parseMinutes(trimmed)
      if (minutes != null && minutes > 0.0) {
        val displayNum =
          if (minutes % 1.0 == 0.0) minutes.toInt().toString() else minutes.toString()
        val reply =
          if (lang == "hi-IN") {
            "$displayNum मिनट का टाइमर शुरू कर दिया है।"
          } else {
            val unit = if (minutes == 1.0) "minute" else "minutes"
            "Timer set for $displayNum $unit."
          }
        return LocalCommandAction.StartTimer(minutes, reply, lang)
      }
    }

    return LocalCommandAction.NotMatched
  }

  private fun parseMinutes(text: String): Double? {
    val match = minuteRegex.find(text)
    if (match != null) {
      return match.groupValues[1].toDoubleOrNull()
    }
    val tokens = text.lowercase(Locale.ROOT).split(Regex("\\s+"))
    for (token in tokens) {
      numberWords[token]?.let { return it }
    }
    return null
  }
}
